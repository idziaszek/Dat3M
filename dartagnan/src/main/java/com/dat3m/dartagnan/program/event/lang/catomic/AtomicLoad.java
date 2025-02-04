package com.dat3m.dartagnan.program.event.lang.catomic;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.core.MemEvent;
import com.dat3m.dartagnan.program.event.core.utils.RegWriter;
import com.dat3m.dartagnan.program.event.visitors.EventVisitor;
import com.google.common.base.Preconditions;

import static com.dat3m.dartagnan.program.event.Tag.C11.MO_ACQUIRE_RELEASE;
import static com.dat3m.dartagnan.program.event.Tag.C11.MO_RELEASE;
import static com.dat3m.dartagnan.program.event.Tag.READ;

public class AtomicLoad extends MemEvent implements RegWriter {

    private final Register resultRegister;

    public AtomicLoad(Register register, IExpr address, String mo) {
        super(address, mo);
        Preconditions.checkArgument(!mo.isEmpty(), "Atomic events cannot have empty memory order");
    	Preconditions.checkArgument(!mo.equals(MO_RELEASE) && !mo.equals(MO_ACQUIRE_RELEASE),
    			getClass().getName() + " can not have memory order: " + mo);
        this.resultRegister = register;
        addFilters(READ);
    }

    private AtomicLoad(AtomicLoad other){
        super(other);
        this.resultRegister = other.resultRegister;
    }

    @Override
    public Register getResultRegister(){
        return resultRegister;
    }

    @Override
    public String toString() {
        return resultRegister + " = atomic_load(*" + address + ", " + mo + ")\t### C11";
    }

    @Override
    public ExprInterface getMemValue(){
        return resultRegister;
    }

	// Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public AtomicLoad getCopy(){
        return new AtomicLoad(this);
    }

	// Visitor
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public <T> T accept(EventVisitor<T> visitor) {
		return visitor.visitAtomicLoad(this);
	}
}