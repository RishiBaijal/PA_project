import java.awt.FlowLayout;
import java.awt.Frame; // Using AWT container and component classes
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.BodyTransformer;
import soot.G;
import soot.PackManager;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.IfStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.VirtualInvokeExpr;
import soot.options.Options;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.ForwardFlowAnalysis;

public class Taint {
	public static void main(String args[]) {
		for (String arg : args) {
			System.out.println(arg);
		}
		PackManager.v().getPack("jtp")
				.add(new Transform("jtp.myTransform", new BodyTransformer() {
					protected void internalTransform(Body body, String phase,
							Map options) {
						new TaintAnalysisWrapper(new ExceptionalUnitGraph(body));
					}
				}));
		Options.v().setPhaseOption("jb", "use-original-names");
		Options.v().set_output_format(Options.output_format_J);
		Options.v().set_src_prec(Options.src_prec_java);
		soot.Main.main(args);
	}

}

class TaintAnalysisWrapper extends Frame {
	public String filepath = "/Users/Rishi/Documents/Master_folder/Semester_7/Program_analysis/Assignments/Workspace/context.txt";
	public String keysFile = "/Users/Rishi/Documents/Master_folder/Semester_7/Program_analysis/Assignments/Workspace/keys.txt";
	public String taintedFile = "/Users/Rishi/Documents/Master_folder/Semester_7/Program_analysis/Assignments/Workspace/taint.txt";
	private String acc = ""; // Accumulated sum, init to 0

	public TaintAnalysisWrapper(UnitGraph graph) {
		setLayout(new FlowLayout());
		Iterator iter = graph.iterator();
		while (iter.hasNext()) {
			// System.out.println(iter.next());
			acc = acc + iter.next().toString() + "\n";
		}
		System.out.println(acc);
		try {
			PrintWriter writer = new PrintWriter(filepath, "UTF-8");
			writer.println(acc);
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		TaintAnalysis analysis = new TaintAnalysis(graph);
		// GraphingData data = new GraphingData(acc, arr);
		// data.setSize(313, 233);
		// jtxt.setTitle("JTextArea font & color settings");
		// data.show();
		// System.out.println(analysis.taintedSinks);
		if (analysis.taintedSinks.size() > 0) {
			G.v().out.print("Failed. ");
			G.v().out.println(analysis.taintedSinks);
		}
		try {
			PrintWriter writer = new PrintWriter(keysFile, "UTF-8");
			Map map = analysis.taintedSinks;
			for (Object key : map.keySet()) {
				writer.println(key.toString());
			}
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	
		try {
			PrintWriter writer = new PrintWriter(taintedFile, "UTF-8");
			Map map = analysis.taintedSinks;
			for (Object key : map.values()) {
				writer.println(key.toString());
			}
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

interface GetUseBoxes {
	public List<ValueBox> getUseBoxes();
}

class TaintAnalysis extends ForwardFlowAnalysis<Unit, Set<Value>> {
	public Map<Unit, Set<Set<Value>>> taintedSinks;

	public TaintAnalysis(UnitGraph graph) {
		super(graph);
		// System.out.println(graph.getBody());

		taintedSinks = new HashMap();
		doAnalysis();
	}

	protected Set<Value> newInitialFlow() {
		return new HashSet();
	}

	protected Set<Value> entryInitialFlow() {
		return new HashSet();
	}

	protected void copy(Set<Value> src, Set<Value> dest) {
		dest.removeAll(dest);
		dest.addAll(src);
	}

	protected void merge(Set<Value> in1, Set<Value> in2, Set<Value> out) {
		out.removeAll(out);
		out.addAll(in1);
		out.addAll(in2);
	}

	protected boolean isTaintedPublicSink(Unit unit, Set<Value> in) {
		if (unit instanceof InvokeStmt) {
			InvokeExpr e = ((InvokeStmt) unit).getInvokeExpr();
			SootMethod sootMethod = e.getMethod();
			if (sootMethod.getName().equals("println")
					&& sootMethod.getDeclaringClass().getName()
							.equals("java.io.PrintStream")
					&& containsValues(in, e)) {
				return true;
			}
		}
		return false;
	}

	protected void flowThrough(Set<Value> in, Unit node, Set<Value> out) {
		Set<Value> filteredIn = stillTaintedValues(in, node);
		Set<Value> newOut = newTaintedValues(filteredIn, node);
		out.removeAll(out);
		out.addAll(filteredIn);
		out.addAll(newOut);

		System.out.println(in);
		// System.out.println(node);
		if (isTaintedPublicSink(node, in)) {
			if (!taintedSinks.containsKey(node)) {
				taintedSinks.put(node, new HashSet());
			}
			taintedSinks.get(node).add(in);
		}
	}

	protected Set<Value> stillTaintedValues(Set<Value> in, Unit node) {
		return in;
	}

	protected boolean containsValue(Value v, Object s) {
		try {
			Method method = s.getClass().getMethod("getUseBoxes");
			for (ValueBox b : (Collection<ValueBox>) method.invoke(s)) {
				if (b.getValue().equals(v)) {
					return true;
				}
			}
			return false;
		} catch (Exception e) {
			return false;
		}
	}

	protected boolean containsValues(Collection<Value> value, Object s) {
		for (Value v : value) {
			if (containsValue(v, s)) {
				return true;
			}
		}
		return false;
	}

	protected boolean isPrivateSource(Value v) {
		if (v instanceof VirtualInvokeExpr) {
			VirtualInvokeExpr e = (VirtualInvokeExpr) v;
			SootMethod sootMethod = e.getMethod();
			if (sootMethod.getName().equals("readLine")
					&& sootMethod.getDeclaringClass().getName()
							.equals("java.io.BufferedReader")) {
				return true;
			}

		}
		return false;
	}

	protected Set<Value> newTaintedValues(Set<Value> in, Unit node) {
		Set<Value> out = new HashSet();
		if (containsValues(in, node)) {
			if (node instanceof AssignStmt) {
				out.add(((AssignStmt) node).getLeftOpBox().getValue());
			} else if (node instanceof IfStmt) {
				IfStmt i = (IfStmt) node;
				if (i.getTarget() instanceof AssignStmt) {
					out.add(((AssignStmt) i.getTarget()).getLeftOpBox()
							.getValue());
				}
			}
		} else if (node instanceof AssignStmt) {
			AssignStmt assignment = (AssignStmt) node;
			if (isPrivateSource(assignment.getRightOpBox().getValue())) {
				out.add(assignment.getLeftOpBox().getValue());
			}
		}
		return out;
	}

}