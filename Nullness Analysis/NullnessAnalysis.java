//package soot.jimple.toolkits.annotation.nullcheck;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import soot.Immediate;
import soot.Local;
import soot.RefLikeType;
import soot.Unit;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.ClassConstant;
import soot.jimple.CaughtExceptionRef;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.MonitorStmt;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.NewMultiArrayExpr;
import soot.jimple.NullConstant;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.ThisRef;
import soot.jimple.internal.AbstractBinopExpr;
import soot.jimple.internal.JCastExpr;
import soot.jimple.internal.JEqExpr;
import soot.jimple.internal.JIfStmt;
import soot.jimple.internal.JInstanceOfExpr;
import soot.jimple.internal.JNeExpr;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.ForwardBranchedFlowAnalysis;



public class NullnessAnalysis  extends ForwardBranchedFlowAnalysis<NullnessAnalysis.AnalysisInfo>
{
	
	protected class AnalysisInfo extends java.util.BitSet
	{
		
		private static final long serialVersionUID = -9200043127757823764L;

		public AnalysisInfo() {
			super(used);
		}

		public AnalysisInfo(AnalysisInfo other) {
			super(used);
			or(other);
		}

		public int get(Value key)
		{
			if (!valueToIndex.containsKey(key))
				return BOTTOM;

			int index = valueToIndex.get(key);
			int result = get(index) ? 2 : 0;
			result += get(index + 1) ? 1 : 0;

			return result;
		}
		
		public void put(Value key, int val)
		{
			int index;
			if (!valueToIndex.containsKey(key)) {
				index = used;
				used += 2;
				valueToIndex.put(key, index);
			} else {
				index = valueToIndex.get(key);
			}
			set(index, (val & 2) == 2);
			set(index + 1, (val & 1) == 1);
		}
	}

	protected final static int BOTTOM = 0;
	protected final static int NULL = 1;
	protected final static int NON_NULL = 2;
	protected final static int TOP = 3;
	
	protected final HashMap<Value,Integer> valueToIndex = new HashMap<Value,Integer>();
	protected int used = 0;

	
	public NullnessAnalysis(UnitGraph graph) {
		super(graph);
		
		doAnalysis();
	}

	
	@Override
	protected void flowThrough(AnalysisInfo in, Unit u, List<AnalysisInfo> fallOut, List<AnalysisInfo> branchOuts) {
		AnalysisInfo out = new AnalysisInfo(in);
		AnalysisInfo outBranch = new AnalysisInfo(in);
		
		Stmt s = (Stmt)u;
		
		if(s instanceof JIfStmt) {
			JIfStmt ifStmt = (JIfStmt) s;
			handleIfStmt(ifStmt, in, out, outBranch);
		}
		else if(s instanceof MonitorStmt) {
			MonitorStmt monitorStmt = (MonitorStmt) s;
			out.put(monitorStmt.getOp(), NON_NULL);
		}
		
		if(s.containsArrayRef()) {
			ArrayRef arrayRef = s.getArrayRef();
			handleArrayRef(arrayRef,out);
		}
		if(s.containsFieldRef()) {
			FieldRef fieldRef = s.getFieldRef();
			handleFieldRef(fieldRef, out);
		}
		if(s.containsInvokeExpr()) {
			InvokeExpr invokeExpr = s.getInvokeExpr();
			handleInvokeExpr(invokeExpr, out);
		}
		
		if(s instanceof DefinitionStmt) {
			DefinitionStmt defStmt = (DefinitionStmt) s;
			if(defStmt.getLeftOp().getType() instanceof RefLikeType) {
				handleRefTypeAssignment(defStmt, out);
			}
		}
		
		for( Iterator<AnalysisInfo> it = fallOut.iterator(); it.hasNext(); ) {
			copy( out, it.next() );
		}
		for( Iterator<AnalysisInfo> it = branchOuts.iterator(); it.hasNext(); ) {
			copy( outBranch, it.next() );
		}
	}
	
	
	protected boolean isAlwaysNonNull(Value v) {
		return false;
	}
	
	private void handleIfStmt(JIfStmt ifStmt, AnalysisInfo in, AnalysisInfo out, AnalysisInfo outBranch) {
		Value condition = ifStmt.getCondition();
		if(condition instanceof JInstanceOfExpr) {
			//a instanceof X ; if this succeeds, a is not null
			JInstanceOfExpr expr = (JInstanceOfExpr) condition;
			handleInstanceOfExpression(expr, in, out, outBranch);
		} else if(condition instanceof JEqExpr || condition instanceof JNeExpr) {
			//a==b or a!=b
			AbstractBinopExpr eqExpr = (AbstractBinopExpr) condition;
			handleEqualityOrNonEqualityCheck(eqExpr, in, out, outBranch);
		} 		
	}

	private void handleEqualityOrNonEqualityCheck(AbstractBinopExpr eqExpr, AnalysisInfo in,
			AnalysisInfo out, AnalysisInfo outBranch) {
		Value left = eqExpr.getOp1();
		Value right = eqExpr.getOp2();
		
		Value val=null;
		if(left==NullConstant.v()) {
			if(right!=NullConstant.v()) {
				val = right;
			}
		} else if(right==NullConstant.v()) {
			if(left!=NullConstant.v()) {
				val = left;
			}
		}
		
		//if we compare a local with null then process further...
		if(val!=null && val instanceof Local) {
			if(eqExpr instanceof JEqExpr)
				//a==null
				handleEquality(val, out, outBranch);
			else if(eqExpr instanceof JNeExpr)
				//a!=null
				handleNonEquality(val, out, outBranch);
			else
				throw new IllegalStateException("unexpected condition: "+eqExpr.getClass());
		}
	}

	private void handleNonEquality(Value val, AnalysisInfo out,
			AnalysisInfo outBranch) {
		out.put(val, NULL);
		outBranch.put(val, NON_NULL);
	}

	private void handleEquality(Value val, AnalysisInfo out,
			AnalysisInfo outBranch) {
		out.put(val, NON_NULL);
		outBranch.put(val, NULL);
	}
	
	private void handleInstanceOfExpression(JInstanceOfExpr expr,
			AnalysisInfo in, AnalysisInfo out, AnalysisInfo outBranch) {
		Value op = expr.getOp();
		//if instanceof succeeds, we have a non-null value
		outBranch.put(op,NON_NULL);
	}

	private void handleArrayRef(ArrayRef arrayRef, AnalysisInfo out) {
		Value array = arrayRef.getBase();
		//here we know that the array must point to an object
		out.put(array, NON_NULL);
	}

	private void handleFieldRef(FieldRef fieldRef,
			AnalysisInfo out) {
		if(fieldRef instanceof InstanceFieldRef) {
			InstanceFieldRef instanceFieldRef = (InstanceFieldRef) fieldRef;
			//here we know that the receiver must point to an object
			Value base = instanceFieldRef.getBase();
			out.put(base,NON_NULL);
		}
	}

	private void handleInvokeExpr(InvokeExpr invokeExpr,AnalysisInfo out) {
		if(invokeExpr instanceof InstanceInvokeExpr) {
			InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
			//here we know that the receiver must point to an object
			Value base = instanceInvokeExpr.getBase();
			out.put(base,NON_NULL);
		}
	}

	private void handleRefTypeAssignment(DefinitionStmt assignStmt, AnalysisInfo out) {
		Value left = assignStmt.getLeftOp();
		Value right = assignStmt.getRightOp();
		
		//unbox casted value
		if(right instanceof JCastExpr) {
			JCastExpr castExpr = (JCastExpr) right;
			right = castExpr.getOp();
		}
		
		//if we have a definition (assignment) statement to a ref-like type, handle it,
		if ( isAlwaysNonNull(right)
		|| right instanceof NewExpr || right instanceof NewArrayExpr
		|| right instanceof NewMultiArrayExpr || right instanceof ThisRef
		|| right instanceof StringConstant || right instanceof ClassConstant
		|| right instanceof CaughtExceptionRef) {
			//if we assign new... or @this, the result is non-null
			out.put(left,NON_NULL);
		} else if(right==NullConstant.v()) {
			//if we assign null, well, it's null
			out.put(left, NULL);
		} else if(left instanceof Local && right instanceof Local) {
			out.put(left, out.get(right));
		} else {
			out.put(left, TOP);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void copy(AnalysisInfo s, AnalysisInfo d) {
		d.clear();
		d.or(s);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void merge(AnalysisInfo in1, AnalysisInfo in2, AnalysisInfo out) {
		out.clear();
		out.or(in1);
		out.or(in2);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected AnalysisInfo newInitialFlow() {
		return new AnalysisInfo();
	}
	
	/**
	 * Returns <code>true</code> if the analysis could determine that i is always null
	 * before the statement s.
	 * @param s a statement of the respective body
	 * @param i a local or constant of that body
	 * @return true if i is always null right before this statement
	 */
	public boolean isAlwaysNullBefore(Unit s, Immediate i) {
		return getFlowBefore(s).get(i) == NULL;
	}

	/**
	 * Returns <code>true</code> if the analysis could determine that i is always non-null
	 * before the statement s.
	 * @param s a statement of the respective body
	 * @param i a local of that body
	 * @return true if i is always non-null right before this statement
	 */
	public boolean isAlwaysNonNullBefore(Unit s, Immediate i) {
		return getFlowBefore(s).get(i) == NON_NULL;
	}

	@Override
	protected AnalysisInfo entryInitialFlow() {
		// TODO Auto-generated method stub
		return null;
	}
}