/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.liftm.wasmerproblem;

import org.apache.flink.runtime.state.StateBackend;
import org.apache.flink.runtime.state.memory.MemoryStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.util.Collector;
import org.wasmer.Instance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * Skeleton for a Flink Streaming Job.
 *
 * <p>For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the <a href="https://flink.apache.org/docs/stable/">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
public class StreamingJob {


	public static void main(String[] args) throws Exception {

		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setParallelism(6);
		env.setStateBackend((StateBackend)new MemoryStateBackend(true));
		env.enableCheckpointing(10, CheckpointingMode.EXACTLY_ONCE);
		env.getCheckpointConfig().setMinPauseBetweenCheckpoints(3000);

		SingleOutputStreamOperator<Integer> initiator = env.addSource(new SourceFunction<Integer>() {
			@Override
			public void run(SourceContext<Integer> ctx) {
				while (true) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException ignored) {
					}
					ctx.collect(new Random().nextInt(42) * 2);
				}
			}

			@Override
			public void cancel() {

			}
		}).name("Inject");

		SingleOutputStreamOperator<Integer> mapper = initiator.process(new ProcessFunction<Integer, Integer>() {
			private transient Instance instance;

			@Override
			public void processElement(Integer integer, Context context, Collector<Integer> collector) throws Exception {
				try {
					if (instance == null) {
						InputStream is = StreamingJob.class.getResourceAsStream("/simple.wasm");
						if (is == null) {
							throw new RuntimeException("No wasm binary!");
						}
						ByteArrayOutputStream buffer = new ByteArrayOutputStream();
						int nRead;
						byte[] data = new byte[16384];
						while ((nRead = is.read(data, 0, data.length)) != -1) {
							buffer.write(data, 0, nRead);
						}
						instance = new Instance(buffer.toByteArray());
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				Object[] results = instance.exports.getFunction("sum").apply(integer, 1);
				collector.collect((int) results[0]);
			}
		}).name("WasmF");

		mapper.addSink(new SinkFunction<Integer>(){
			@Override
			public void invoke(Integer value, Context context) throws Exception {
				System.out.println("V" + value);
			}
		});

		// execute program
		env.execute("ZZZZZ");
	}
}
