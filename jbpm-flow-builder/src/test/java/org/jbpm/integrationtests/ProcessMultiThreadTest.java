package org.jbpm.integrationtests;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.drools.compiler.compiler.DroolsError;
import org.drools.compiler.compiler.PackageBuilder;
import org.drools.core.RuleBase;
import org.drools.core.RuleBaseFactory;
import org.drools.core.StatefulSession;
import org.jbpm.test.util.AbstractBaseTest;
import org.junit.Test;
import org.kie.api.runtime.process.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessMultiThreadTest extends AbstractBaseTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessMultiThreadTest.class);

    @Test
    public void testMultiThreadProcessInstanceSignalling() {
        final int THREAD_COUNT = 2;
        try {
            boolean success = true;
            final Thread[] t = new Thread[THREAD_COUNT];
            
            final PackageBuilder builder = new PackageBuilder();
            builder.addProcessFromXml(new InputStreamReader( getClass().getResourceAsStream( "test_ProcessMultithreadEvent.rf" ) ) );
            if (builder.getErrors().getErrors().length > 0) {
            	for (DroolsError error: builder.getErrors().getErrors()) {
            	    logger.error(error.toString());
            	}
            	fail("Could not parse process");
            }
            RuleBase ruleBase = RuleBaseFactory.newRuleBase();
            ruleBase.addPackage( builder.getPackage() );
            ruleBase = SerializationHelper.serializeObject(ruleBase);
            StatefulSession session = ruleBase.newStatefulSession();
            session = SerializationHelper.getSerialisedStatefulSession(session);
            List<String> list = new ArrayList<String>();
            session.setGlobal("list", list);
            ProcessInstance processInstance = session.startProcess("org.drools.integrationtests.multithread");
            final ProcessInstanceSignalRunner[] r = new ProcessInstanceSignalRunner[THREAD_COUNT];
            for ( int i = 0; i < t.length; i++ ) {
                r[i] = new ProcessInstanceSignalRunner(i, processInstance, "event" + (i+1));
                t[i] = new Thread( r[i], "thread-" + i );
                t[i].start();
            }
            for ( int i = 0; i < t.length; i++ ) {
                t[i].join();
                if ( r[i].getStatus() == ProcessInstanceSignalRunner.Status.FAIL ) {
                    success = false;
                }
            }
            if ( !success ) {
                fail( "Multithread test failed. Look at the stack traces for details. " );
            }
            assertEquals(2, list.size());
            assertFalse(list.get(0).equals(list.get(1)));
            assertEquals(ProcessInstance.STATE_COMPLETED, processInstance.getState());
        } catch ( Exception e ) {
            e.printStackTrace();
            fail( "Should not raise any exception: " + e.getMessage() );
        }
	}
	
    public static class ProcessInstanceSignalRunner implements Runnable {

	    private ProcessInstance processInstance;
	    private String type;
        private Status status;
        private int id;
	
	    public ProcessInstanceSignalRunner(int id, ProcessInstance processInstance, String type) {
	        this.id = id;
	    	this.processInstance = processInstance;
	    	this.type = type;
	        this.status = Status.SUCCESS;
	    }
	
	    public void run() {
	        try {
	        	processInstance.signalEvent(type, null);
	        } catch ( Exception e ) {
	            this.status = Status.FAIL;
	            logger.warn("{} failed: {}",Thread.currentThread().getName(), e.getMessage());
	        }
	    }
	
	    public static enum Status {
	        SUCCESS, FAIL
	    }
	
	    public int getId() {
	        return id;
	    }
	
	    public Status getStatus() {
	        return status;
	    }
	
	}
    
}
