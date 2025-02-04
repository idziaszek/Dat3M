package com.dat3m.dartagnan.program.event.core;

import com.dat3m.dartagnan.expression.BExpr;
import com.dat3m.dartagnan.program.event.visitors.EventVisitor;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

public class IfAsJump extends CondJump {

	private final Label end;
	
	public IfAsJump(BExpr expr, Label label, Label end) {
		super(expr, label);
		this.end = end;
	}
	
    protected IfAsJump(IfAsJump other) {
		super(other);
		this.end = other.end;
	}

	public Label getEndIf() { return end; }

    public List<Event> getBranchesEvents(){
    	// Because it is used for RelCtrlDirect
    	Preconditions.checkState(cId > -1, "getBranchesEvents() must be called after compilation");
		List<Event> events = new ArrayList<>();
		Event next = getSuccessor();
		// For IfAsJump events, getLabel() returns the label representing the else branch
		while(next != null && next.getSuccessor() != getLabel()) {
			events.add(next);
			next = next.getSuccessor();
		}
		next = getLabel().getSuccessor();
		while(next != end && next != null) {
			events.add(next);
			next = next.getSuccessor();
		}
		return events;
    }

	@Override
	public IfAsJump getCopy() {
		return new IfAsJump(this);
	}

	// Visitor
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public <T> T accept(EventVisitor<T> visitor) {
		return visitor.visitIfAsJump(this);
	}
}