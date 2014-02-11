package brooklyn.location.jclouds;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.exceptions.CompoundRuntimeException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class AbstractJcloudsTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractJcloudsTest.class);

    public static final String BROOKLYN_PROPERTIES_PREFIX = "brooklyn.location.jclouds.";
    public static final String BROOKLYN_PROPERTIES_LEGACY_PREFIX = "brooklyn.jclouds.";
    
    public static final String AWS_EC2_PROVIDER = "aws-ec2";
    public static final String AWS_EC2_TINY_HARDWARE_ID = "t1.micro";
    public static final String AWS_EC2_SMALL_HARDWARE_ID = "m1.small";
    
    public static final String RACKSPACE_PROVIDER = "rackspace-cloudservers-uk";
    
    protected BrooklynProperties brooklynProperties;
    protected LocalManagementContext managementContext;
    
    protected List<JcloudsSshMachineLocation> machines;
    protected JcloudsLocation jcloudsLocation;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        machines = Lists.newCopyOnWriteArrayList();
        managementContext = new LocalManagementContext();
        
        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        brooklynProperties = managementContext.getBrooklynProperties();
        for (String key : ImmutableSet.copyOf(brooklynProperties.asMapWithStringKeys().keySet())) {
            if (key.startsWith(BROOKLYN_PROPERTIES_PREFIX) && !(key.endsWith("identity") || key.endsWith("credential"))) {
                brooklynProperties.remove(key);
            }
            if (key.startsWith(BROOKLYN_PROPERTIES_LEGACY_PREFIX) && !(key.endsWith("identity") || key.endsWith("credential"))) {
                brooklynProperties.remove(key);
            }
            
            // Also removes scriptHeader (e.g. if doing `. ~/.bashrc` and `. ~/.profile`, then that can cause "stdin: is not a tty")
            if (key.startsWith("brooklyn.ssh")) {
                brooklynProperties.remove(key);
            }
        }
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        List<Exception> exceptions = Lists.newArrayList();
        try {
            for (JcloudsSshMachineLocation machine : machines) {
                try {
                    releaseMachine(machine);
                } catch (Exception e) {
                    LOG.warn("Error releasing machine "+machine+"; continuing...", e);
                    exceptions.add(e);
                }
            }
            machines.clear();
        } finally {
            try {
                if (managementContext != null) Entities.destroyAll(managementContext);
            } catch (Exception e) {
                LOG.warn("Error destroying management context", e);
                exceptions.add(e);
            }
        }
        
        // TODO Debate about whether to:
        //  - use destroyAllCatching (i.e. not propagating exception)
        //    Benefit is that other tests in class will subsequently be run, rather than bailing out.
        //  - propagate exceptions from tearDown
        //    Benefit is that we don't hide errors; release(...) etc should not be throwing exceptions.
        if (exceptions.size() > 0) {
            throw new CompoundRuntimeException("Error in tearDown of "+getClass(), exceptions);
        }
    }

    protected void assertSshable(SshMachineLocation machine) {
        int result = machine.execScript("simplecommand", ImmutableList.of("true"));
        assertEquals(result, 0);
    }

    // Use this utility method to ensure machines are released on tearDown
    protected JcloudsSshMachineLocation obtainMachine(Map<?, ?> conf) throws Exception {
        assertNotNull(jcloudsLocation);
        JcloudsSshMachineLocation result = jcloudsLocation.obtain(conf);
        machines.add(result);
        return result;
    }

    protected JcloudsSshMachineLocation obtainMachine() throws Exception {
        return obtainMachine(ImmutableMap.of());
    }
    
    protected void releaseMachine(JcloudsSshMachineLocation machine) {
        assertNotNull(jcloudsLocation);
        machines.remove(machine);
        jcloudsLocation.release(machine);
    }
}
