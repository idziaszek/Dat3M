package com.dat3m.dartagnan.parsers.boogie;

import java.util.List;
import com.dat3m.dartagnan.parsers.BoogieParser.ExprContext;
import com.dat3m.dartagnan.parsers.BoogieParser.Var_or_typeContext;

public class Function {

	private String name;
    private List<Var_or_typeContext> signature;
    private ExprContext body;
    
    public Function(String name, List<Var_or_typeContext> signature, ExprContext body) {
    	this.name = name;
    	this.signature = signature;
    	this.body = body;
    }
    
    public List<Var_or_typeContext> getSignature() {
    	return signature;
    }
    
    public ExprContext getBody() {
    	return body;
    }

    public String getName() {
    	return name;
    }
}
