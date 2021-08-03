package com.dat3m.ui.result;

import static com.dat3m.dartagnan.analysis.Base.runAnalysisIncrementalSolver;

import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.api.SolverContext;

import static com.dat3m.dartagnan.analysis.Base.runAnalysisTwoSolvers;
import static com.dat3m.dartagnan.analysis.Base.runAnalysisAssumeSolver;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.dat3m.ui.utils.UiOptions;
import com.dat3m.ui.utils.Utils;

public class ReachabilityResult {

    private final Program program;
    private final Wmm wmm;
    private final UiOptions options;

    private String verdict;

    public ReachabilityResult(Program program, Wmm wmm, UiOptions options){
        this.program = program;
        this.wmm = wmm;
        this.options = options;
        run();
    }
    
    public String getVerdict(){
        return verdict;
    }

    private void run(){
        if(validate()){
            VerificationTask task = new VerificationTask(program, wmm, program.getArch() != null ? program.getArch() : options.getTarget(), options.getSettings());
            Result result = Result.UNKNOWN;

            ShutdownManager sdm = ShutdownManager.create();
        	Thread t = new Thread(() -> {
    			try {
    				if(options.getSettings().getSolverTimeout() > 0) {
    					// Converts timeout from secs to millisecs
    					Thread.sleep(1000 * options.getSettings().getSolverTimeout());
    					sdm.requestShutdown("Shutdown Request");
    				}
    			} catch (InterruptedException e) {
    				throw new UnsupportedOperationException("Unexpected interrupt");
    			}});

            try {
            	t.start();
                Configuration config = Configuration.builder()
                		.setOption("solver.z3.usePhantomReferences", "true")
                		.build();
				SolverContext ctx = SolverContextFactory.createSolverContext(
                        config, 
                        BasicLogManager.create(config), 
                        sdm.getNotifier(), 
                        options.getSolver());
                
                switch(options.getMethod()) {
            	case INCREMENTAL:
            		result = runAnalysisIncrementalSolver(ctx, task);
            		break;
            	case ASSUME:
            		result = runAnalysisAssumeSolver(ctx, task);
            		break;
            	case TWOSOLVERS:
                    result = runAnalysisTwoSolvers(ctx, task);
                    break;
                }
                buildVerdict(result);
                ctx.close();
            } catch (InterruptedException e){
            	verdict = "TIMEOUT";
            } catch (Exception e) {
            	verdict = "ERROR: " + e.getMessage();
            }
        }
    }

    private void buildVerdict(Result result){
        StringBuilder sb = new StringBuilder();
        sb.append("Condition ").append(program.getAss().toStringWithType()).append("\n");
        sb.append(result).append("\n");
        verdict = sb.toString();
    }

    private boolean validate(){
        Arch target = program.getArch() == null ? options.getTarget() : program.getArch();
        if(target == null) {
            Utils.showError("Missing target architecture.");
            return false;
        }
        program.setArch(target);
        return true;
    }
}
