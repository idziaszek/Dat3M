package dartagnan.expression;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import dartagnan.expression.op.COpBin;
import dartagnan.program.Register;
import dartagnan.utils.MapSSA;

import java.util.HashSet;
import java.util.Set;

public class Atom extends BExpr implements ExprInterface {
	
	private ExprInterface lhs;
	private ExprInterface rhs;
	private COpBin op;
	
	public Atom (ExprInterface lhs, COpBin op, ExprInterface rhs) {
		this.lhs = lhs;
		this.rhs = rhs;
		this.op = op;
	}

    @Override
	public BoolExpr toZ3Bool(MapSSA map, Context ctx) {
		return op.encode(lhs.toZ3Int(map, ctx), rhs.toZ3Int(map, ctx), ctx);
	}

    @Override
	public Set<Register> getRegs() {
		Set<Register> setRegs = new HashSet<>();
		setRegs.addAll(lhs.getRegs());
		setRegs.addAll(rhs.getRegs());
		return setRegs;
	}

    @Override
    public Atom clone() {
        return new Atom(lhs.clone(), op, rhs.clone());
    }

    @Override
    public String toString() {
        return lhs + " " + op + " " + rhs;
    }
}