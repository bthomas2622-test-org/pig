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
package org.apache.pig.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;

import junit.framework.Assert;

import org.apache.pig.ExecType;
import org.apache.pig.PigServer;
import org.apache.pig.ResourceSchema;
import org.apache.pig.backend.datastorage.DataStorage;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.hadoop.datastorage.ConfigurationUtil;
import org.apache.pig.builtin.BinStorage;
import org.apache.pig.builtin.PigStorage;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataType;
import org.apache.pig.data.DefaultBagFactory;
import org.apache.pig.data.DefaultTuple;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.PigContext;
import org.apache.pig.impl.logicalLayer.LOStore;
import org.apache.pig.impl.logicalLayer.LogicalOperator;
import org.apache.pig.impl.logicalLayer.LogicalPlan;
import org.apache.pig.impl.logicalLayer.LogicalPlanBuilder;
import org.apache.pig.impl.logicalLayer.parser.ParseException;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.impl.plan.OperatorKey;
import org.apache.pig.test.utils.GenRandomData;
import org.apache.pig.test.utils.TestHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestStore extends junit.framework.TestCase {
    
    DataBag inpDB;
    static MiniCluster cluster = MiniCluster.buildCluster();
    PigServer pig;
    PigContext pc;
    String inputFileName;
    String outputFileName;
    
    @Override
    @Before
    public void setUp() throws Exception {
        pig = new PigServer(ExecType.MAPREDUCE, cluster.getProperties());
        pc = pig.getPigContext();
        inputFileName = "/tmp/TestStore-" + new Random().nextLong() + ".txt";
        outputFileName = "/tmp/TestStore-output-" + new Random().nextLong() + 
        ".txt";
    }

    @Override
    @After
    public void tearDown() throws Exception {
        Util.deleteFile(cluster, inputFileName);
        Util.deleteFile(cluster, outputFileName);
        new File(outputFileName).delete();
    }

    private void storeAndCopyLocally(DataBag inpDB) throws Exception {
        setUpInputFileOnCluster(inpDB);
        String script = "a = load '" + inputFileName + "'; " +
        "store a into '" + outputFileName + "' using PigStorage(':');" +
        		"fs -ls /tmp";
        pig.setBatchOn();
        Util.registerMultiLineQuery(pig, script);
        pig.executeBatch();
        Util.copyFromClusterToLocal(cluster, outputFileName + "/part-m-00000", outputFileName);
    }

    @Test
    public void testStore() throws Exception {
        inpDB = GenRandomData.genRandSmallTupDataBag(new Random(), 10, 100);
        storeAndCopyLocally(inpDB);
        
        int size = 0;
        BufferedReader br = new BufferedReader(new FileReader(outputFileName));
        for(String line=br.readLine();line!=null;line=br.readLine()){
            String[] flds = line.split(":",-1);
            Tuple t = new DefaultTuple();
            t.append(flds[0].compareTo("")!=0 ? flds[0] : null);
            t.append(flds[1].compareTo("")!=0 ? Integer.parseInt(flds[1]) : null);
            
            System.err.println("Simple data: ");
            System.err.println(line);
            System.err.println("t: ");
            System.err.println(t);
            assertEquals(true, TestHelper.bagContains(inpDB, t));
            ++size;
        }
        assertEquals(true, size==inpDB.size());
    }

    /**
     * @param inpD
     * @throws IOException 
     */
    private void setUpInputFileOnCluster(DataBag inpD) throws IOException {
        String[] data = new String[(int) inpD.size()];
        int i = 0;
        for (Tuple tuple : inpD) {
            data[i] = toDelimitedString(tuple, "\t");
            i++;
        } 
        Util.createInputFile(cluster, inputFileName, data);
    }
    
    @SuppressWarnings("unchecked")
    private String toDelimitedString(Tuple t, String delim) throws ExecException {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < t.size(); i++) {
            Object field = t.get(i);
            if(field == null) {
                buf.append("");
            } else {
                if(field instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>)field;
                    buf.append(DataType.mapToString(m));
                } else {
                    buf.append(field.toString());
                }
            }
            
            if (i != t.size() - 1)
                buf.append(delim);
        }
        return buf.toString();
    }

    @Test
    public void testStoreComplexData() throws Exception {
        inpDB = GenRandomData.genRandFullTupTextDataBag(new Random(), 10, 100);
        storeAndCopyLocally(inpDB);
        PigStorage ps = new PigStorage(":");
                
        int size = 0;
        BufferedReader br = new BufferedReader(new FileReader(outputFileName));
        for(String line=br.readLine();line!=null;line=br.readLine()){
            String[] flds = line.split(":",-1);
            Tuple t = new DefaultTuple();
            t.append(flds[0].compareTo("")!=0 ? ps.getLoadCaster().bytesToBag(flds[0].getBytes()) : null);
            t.append(flds[1].compareTo("")!=0 ? ps.getLoadCaster().bytesToCharArray(flds[1].getBytes()) : null);
            t.append(flds[2].compareTo("")!=0 ? ps.getLoadCaster().bytesToCharArray(flds[2].getBytes()) : null);
            t.append(flds[3].compareTo("")!=0 ? ps.getLoadCaster().bytesToDouble(flds[3].getBytes()) : null);
            t.append(flds[4].compareTo("")!=0 ? ps.getLoadCaster().bytesToFloat(flds[4].getBytes()) : null);
            t.append(flds[5].compareTo("")!=0 ? ps.getLoadCaster().bytesToInteger(flds[5].getBytes()) : null);
            t.append(flds[6].compareTo("")!=0 ? ps.getLoadCaster().bytesToLong(flds[6].getBytes()) : null);
            t.append(flds[7].compareTo("")!=0 ? ps.getLoadCaster().bytesToMap(flds[7].getBytes()) : null);
            t.append(flds[8].compareTo("")!=0 ? ps.getLoadCaster().bytesToTuple(flds[8].getBytes()) : null);
            assertEquals(true, TestHelper.bagContains(inpDB, t));
            ++size;
        }
        assertEquals(true, size==inpDB.size());
    }

    @Test
    public void testStoreComplexDataWithNull() throws Exception {
        Tuple inputTuple = GenRandomData.genRandSmallBagTextTupleWithNulls(new Random(), 10, 100);
        inpDB = DefaultBagFactory.getInstance().newDefaultBag();
        inpDB.add(inputTuple);
        storeAndCopyLocally(inpDB);
        PigStorage ps = new PigStorage(":");
        int size = 0;
        BufferedReader br = new BufferedReader(new FileReader(outputFileName));
        for(String line=br.readLine();line!=null;line=br.readLine()){
            System.err.println("Complex data: ");
            System.err.println(line);
            String[] flds = line.split(":",-1);
            Tuple t = new DefaultTuple();
            t.append(flds[0].compareTo("")!=0 ? ps.getLoadCaster().bytesToBag(flds[0].getBytes()) : null);
            t.append(flds[1].compareTo("")!=0 ? ps.getLoadCaster().bytesToCharArray(flds[1].getBytes()) : null);
            t.append(flds[2].compareTo("")!=0 ? ps.getLoadCaster().bytesToCharArray(flds[2].getBytes()) : null);
            t.append(flds[3].compareTo("")!=0 ? ps.getLoadCaster().bytesToDouble(flds[3].getBytes()) : null);
            t.append(flds[4].compareTo("")!=0 ? ps.getLoadCaster().bytesToFloat(flds[4].getBytes()) : null);
            t.append(flds[5].compareTo("")!=0 ? ps.getLoadCaster().bytesToInteger(flds[5].getBytes()) : null);
            t.append(flds[6].compareTo("")!=0 ? ps.getLoadCaster().bytesToLong(flds[6].getBytes()) : null);
            t.append(flds[7].compareTo("")!=0 ? ps.getLoadCaster().bytesToMap(flds[7].getBytes()) : null);
            t.append(flds[8].compareTo("")!=0 ? ps.getLoadCaster().bytesToTuple(flds[8].getBytes()) : null);
            t.append(flds[9].compareTo("")!=0 ? ps.getLoadCaster().bytesToCharArray(flds[9].getBytes()) : null);
            assertTrue(TestHelper.tupleEquals(inputTuple, t));
            ++size;
        }
    }
    @Test
    public void testBinStorageGetSchema() throws IOException, ParseException {
        String input[] = new String[] { "hello\t1\t10.1", "bye\t2\t20.2" };
        String inputFileName = "testGetSchema-input.txt";
        String outputFileName = "testGetSchema-output.txt";
        try {
            Util.createInputFile(pig.getPigContext(), 
                    inputFileName, input);
            String query = "a = load '" + inputFileName + "' as (c:chararray, " +
                    "i:int,d:double);store a into '" + outputFileName + "' using " +
                            "BinStorage();";
            pig.setBatchOn();
            Util.registerMultiLineQuery(pig, query);
            pig.executeBatch();
            ResourceSchema rs = new BinStorage().getSchema(outputFileName, 
                    ConfigurationUtil.toConfiguration(pig.getPigContext().
                            getProperties()));
            Schema expectedSchema = Util.getSchemaFromString(
                    "c:chararray,i:int,d:double");
            Assert.assertTrue("Checking binstorage getSchema output", Schema.equals( 
                    expectedSchema, Schema.getPigSchema(rs), true, true));
        } finally {
            Util.deleteFile(pig.getPigContext(), inputFileName);
            Util.deleteFile(pig.getPigContext(), outputFileName);
        }
    }

    private static void randomizeBytes(byte[] data, int offset, int length) {
        Random random = new Random();
        for(int i=offset + length - 1; i >= offset; --i) {
            data[i] = (byte) random.nextInt(256);
        }
    }

    
    @Test
    public void testStoreRemoteRel() throws Exception {
        checkStorePath("test","/tmp/test");
    }

    @Test
    public void testStoreRemoteAbs() throws Exception {
        checkStorePath("/tmp/test","/tmp/test");
    }

    @Test
    public void testStoreRemoteRelScheme() throws Exception {
        checkStorePath("test","/tmp/test");
    }

    @Test
    public void testStoreRemoteAbsScheme() throws Exception {
        checkStorePath("hdfs:/tmp/test","/tmp/test");
    }

    @Test
    public void testStoreRemoteAbsAuth() throws Exception {
        checkStorePath("hdfs://localhost:9000/test","/test");
    }

    @Test
    public void testStoreRemoteNormalize() throws Exception {
        checkStorePath("/tmp/foo/../././","/tmp");
    }

    private void checkStorePath(String orig, String expected) throws Exception {
        checkStorePath(orig, expected, false);
    }

    private void checkStorePath(String orig, String expected, boolean isTmp) throws Exception {
        pc.getProperties().setProperty("opt.multiquery",""+true);

        DataStorage dfs = pc.getDfs();
        dfs.setActiveContainer(dfs.asContainer("/tmp"));
        Map<LogicalOperator, LogicalPlan> aliases = new HashMap<LogicalOperator, LogicalPlan>();
        Map<OperatorKey, LogicalOperator> logicalOpTable = new HashMap<OperatorKey, LogicalOperator>();
        Map<String, LogicalOperator> aliasOp = new HashMap<String, LogicalOperator>();
        Map<String, String> fileNameMap = new HashMap<String, String>();
        
        LogicalPlanBuilder builder = new LogicalPlanBuilder(pc);
        
        String query = "a = load 'foo';";
        LogicalPlan lp = builder.parse("Test-Store",
                                       query,
                                       aliases,
                                       logicalOpTable,
                                       aliasOp,
                                       fileNameMap);
        query = "store a into '"+orig+"';";
        lp = builder.parse("Test-Store",
                           query,
                           aliases,
                           logicalOpTable,
                           aliasOp,
                           fileNameMap);

        Assert.assertTrue(lp.size()>1);
        LogicalOperator op = lp.getLeaves().get(0);
        
        Assert.assertTrue(op instanceof LOStore);
        LOStore store = (LOStore)op;

        String p = store.getOutputFile().getFileName();
        p = p.replaceAll("hdfs://[0-9a-zA-Z:\\.]*/","/");
        
        if (isTmp) {
            Assert.assertTrue(p.matches("/tmp.*"));
        } else {
            Assert.assertEquals(p, expected);
        }
    }
}
