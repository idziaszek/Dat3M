package com.dat3m.dartagnan;

import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.ResourceHelper;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.dat3m.dartagnan.wmm.utils.alias.Alias;
import com.microsoft.z3.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.dat3m.dartagnan.utils.ResourceHelper.TEST_RESOURCE_PATH;
import static com.dat3m.dartagnan.utils.Result.UNKNOWN;
import static com.dat3m.dartagnan.wmm.utils.Arch.ARM8;
import static com.dat3m.dartagnan.wmm.utils.Arch.POWER;
import static com.dat3m.dartagnan.wmm.utils.Arch.TSO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class SafeCTest {

	static final int TIMEOUT = 180000;

    private final String path;
    private final Wmm wmm;
    private final Arch target;
    private final Settings settings;

	@Parameterized.Parameters(name = "{index}: {0} target={2}")
    public static Iterable<Object[]> data() throws IOException {
        Wmm tso = new ParserCat().parse(new File(ResourceHelper.CAT_RESOURCE_PATH + "cat/tso.cat"));
        Wmm power = new ParserCat().parse(new File(ResourceHelper.CAT_RESOURCE_PATH + "cat/power.cat"));
        Wmm arm = new ParserCat().parse(new File(ResourceHelper.CAT_RESOURCE_PATH + "cat/aarch64.cat"));

        Settings s1 = new Settings(Alias.CFIS, 1, TIMEOUT);

        List<Object[]> data = new ArrayList<>();

        data.add(new Object[]{TEST_RESOURCE_PATH + "locks/safe/ttas-5-O0.bpl", tso, TSO, s1});
        data.add(new Object[]{TEST_RESOURCE_PATH + "locks/safe/ttas-5-O0.bpl", arm, ARM8, s1});
        data.add(new Object[]{TEST_RESOURCE_PATH + "locks/safe/ttas-5-O0.bpl", power, POWER, s1});

        data.add(new Object[]{TEST_RESOURCE_PATH + "locks/safe/ticketlock-6-O0.bpl", tso, TSO, s1});
        data.add(new Object[]{TEST_RESOURCE_PATH + "locks/safe/ticketlock-6-O0.bpl", arm, ARM8, s1});
        data.add(new Object[]{TEST_RESOURCE_PATH + "locks/safe/ticketlock-6-O0.bpl", power, POWER, s1});

        data.add(new Object[]{TEST_RESOURCE_PATH + "locks/safe/mutex-4-O0.bpl", tso, TSO, s1});
        data.add(new Object[]{TEST_RESOURCE_PATH + "locks/safe/mutex-4-O0.bpl", arm, ARM8, s1});
        data.add(new Object[]{TEST_RESOURCE_PATH + "locks/safe/mutex-4-O0.bpl", power, POWER, s1});

        data.add(new Object[]{TEST_RESOURCE_PATH + "locks/safe/mutex_musl-4-O0.bpl", tso, TSO, s1});
        data.add(new Object[]{TEST_RESOURCE_PATH + "locks/safe/mutex_musl-4-O0.bpl", arm, ARM8, s1});
        data.add(new Object[]{TEST_RESOURCE_PATH + "locks/safe/mutex_musl-4-O0.bpl", power, POWER, s1});

        return data;
    }

    public SafeCTest(String path, Wmm wmm, Arch target, Settings settings) {
        this.path = path;
        this.wmm = wmm;
        this.target = target;
        this.settings = settings;
    }
    
    @Test(timeout = TIMEOUT)
    public void test() {
    	try {
            Program program = new ProgramParser().parse(new File(path));
            Context ctx = new Context();
            VerificationTask task = new VerificationTask(program, wmm, target, settings);
            assertEquals(UNKNOWN, com.dat3m.dartagnan.analysis.Base.runAnalysisAssumeSolver(ctx.mkSolver(), ctx, task));
            ctx.close();
        } catch (IOException e){
            fail("Missing resource file");
        }
    }
}