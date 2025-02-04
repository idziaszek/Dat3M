package com.dat3m.dartagnan.parsers.program.visitors.boogie;

import com.dat3m.dartagnan.exception.MalformedProgramException;
import com.dat3m.dartagnan.exception.ParsingException;
import com.dat3m.dartagnan.expression.*;
import com.dat3m.dartagnan.parsers.BoogieParser.Call_cmdContext;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.EventFactory;

import java.util.Arrays;
import java.util.List;

public class SvcompProcedures {

	public static List<String> SVCOMPPROCEDURES = Arrays.asList(
			"__VERIFIER_assert",
			// "__VERIFIER_assume",
			"__VERIFIER_loop_bound",
			"__VERIFIER_loop_begin",
			"__VERIFIER_spin_start",
			"__VERIFIER_spin_end",
			"__VERIFIER_atomic_begin",
			"__VERIFIER_atomic_end",
			"__VERIFIER_nondet_bool",
			"__VERIFIER_nondet_int",
			"__VERIFIER_nondet_uint",
			"__VERIFIER_nondet_unsigned_int",
			"__VERIFIER_nondet_short",
			"__VERIFIER_nondet_ushort",
			"__VERIFIER_nondet_unsigned_short",
			"__VERIFIER_nondet_long",
			"__VERIFIER_nondet_ulong",
			"__VERIFIER_nondet_char",
			"__VERIFIER_nondet_uchar");

	public static void handleSvcompFunction(VisitorBoogie visitor, Call_cmdContext ctx) {
		String name = ctx.call_params().Define() == null ? ctx.call_params().Ident(0).getText() : ctx.call_params().Ident(1).getText();
		switch(name) {
			case "__VERIFIER_loop_bound":
				__VERIFIER_loop_bound(visitor, ctx);
				break;
			case "__VERIFIER_loop_begin":
				visitor.programBuilder.addChild(visitor.threadCount, EventFactory.Svcomp.newLoopBegin());
				break;
			case "__VERIFIER_spin_start":
				visitor.programBuilder.addChild(visitor.threadCount, EventFactory.Svcomp.newSpinStart());
				break;
			case "__VERIFIER_spin_end":
				visitor.programBuilder.addChild(visitor.threadCount, EventFactory.Svcomp.newSpinEnd());
				break;
			case "__VERIFIER_assert":
				visitor.addAssertion((IExpr)ctx.call_params().exprs().accept(visitor));
				break;
			case "__VERIFIER_assume":
				__VERIFIER_assume(visitor, ctx);
				break;
			case "__VERIFIER_atomic_begin":
				__VERIFIER_atomic_begin(visitor);
				break;
			case "__VERIFIER_atomic_end":
				__VERIFIER_atomic_end(visitor);
				break;
			case "__VERIFIER_nondet_bool":
				__VERIFIER_nondet_bool(visitor, ctx);
				break;
			case "__VERIFIER_nondet_int":
			case "__VERIFIER_nondet_uint":
			case "__VERIFIER_nondet_unsigned_int":
			case "__VERIFIER_nondet_short":
			case "__VERIFIER_nondet_ushort":
			case "__VERIFIER_nondet_unsigned_short":
			case "__VERIFIER_nondet_long":
			case "__VERIFIER_nondet_ulong":
			case "__VERIFIER_nondet_char":
			case "__VERIFIER_nondet_uchar":
				__VERIFIER_nondet(visitor, ctx, name);
				break;
			default:
				throw new UnsupportedOperationException(name + " procedure is not part of SVCOMPPROCEDURES");
		}
	}

	private static void __VERIFIER_assume(VisitorBoogie visitor, Call_cmdContext ctx) {
    	ExprInterface expr = (ExprInterface)ctx.call_params().exprs().accept(visitor);
       	visitor.programBuilder.addChild(visitor.threadCount, EventFactory.newAssume(expr));
	}
	
	public static void __VERIFIER_atomic_begin(VisitorBoogie visitor) {
		visitor.currentBeginAtomic = EventFactory.Svcomp.newBeginAtomic();
		visitor.programBuilder.addChild(visitor.threadCount, visitor.currentBeginAtomic);	
	}
	
	public static void __VERIFIER_atomic_end(VisitorBoogie visitor) {
		if(visitor.currentBeginAtomic == null) {
            throw new MalformedProgramException("__VERIFIER_atomic_end() does not have a matching __VERIFIER_atomic_begin()");
		}
		visitor.programBuilder.addChild(visitor.threadCount, EventFactory.Svcomp.newEndAtomic(visitor.currentBeginAtomic));
		visitor.currentBeginAtomic = null;
	}
	
	private static void __VERIFIER_nondet(VisitorBoogie visitor, Call_cmdContext ctx, String name) {
		INonDetTypes type = null;
		switch (name) {
			case "__VERIFIER_nondet_int":
				type = INonDetTypes.INT;
				break;
			case "__VERIFIER_nondet_uint":
			case "__VERIFIER_nondet_unsigned_int":
				type = INonDetTypes.UINT;
				break;
			case "__VERIFIER_nondet_short":
				type = INonDetTypes.SHORT;
				break;
			case "__VERIFIER_nondet_ushort":
			case "__VERIFIER_nondet_unsigned_short":
				type = INonDetTypes.USHORT;
				break;
			case "__VERIFIER_nondet_long":
				type = INonDetTypes.LONG;
				break;
			case "__VERIFIER_nondet_ulong":
				type = INonDetTypes.ULONG;
				break;
			case "__VERIFIER_nondet_char":
				type = INonDetTypes.CHAR;
				break;
			case "__VERIFIER_nondet_uchar":
				type = INonDetTypes.UCHAR;
				break;
			default:
				throw new ParsingException(name + " is not supported");
		}
		String registerName = ctx.call_params().Ident(0).getText();
		Register register = visitor.programBuilder.getRegister(visitor.threadCount, visitor.currentScope.getID() + ":" + registerName);
	    if(register != null){
			visitor.programBuilder.addChild(visitor.threadCount, EventFactory.newLocal(register, new INonDet(type, register.getPrecision())))
					.setCFileInformation(visitor.currentLine, visitor.sourceCodeFile);
	    }
	}

	private static void __VERIFIER_nondet_bool(VisitorBoogie visitor, Call_cmdContext ctx) {
		String registerName = ctx.call_params().Ident(0).getText();
		Register register = visitor.programBuilder.getRegister(visitor.threadCount, visitor.currentScope.getID() + ":" + registerName);
	    if(register != null){
			visitor.programBuilder.addChild(visitor.threadCount, EventFactory.newLocal(register, new BNonDet()))
					.setCFileInformation(visitor.currentLine, visitor.sourceCodeFile);
	    }
	}

	private static void __VERIFIER_loop_bound(VisitorBoogie visitor, Call_cmdContext ctx) {
		int bound = ((IExpr)ctx.call_params().exprs().expr(0).accept(visitor)).reduce().getValueAsInt();
		visitor.programBuilder.addChild(visitor.threadCount, EventFactory.Svcomp.newLoopBound(bound))
				.setCFileInformation(visitor.currentLine, visitor.sourceCodeFile);
	}
}
