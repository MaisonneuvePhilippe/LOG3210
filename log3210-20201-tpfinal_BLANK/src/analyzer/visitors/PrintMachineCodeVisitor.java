package analyzer.visitors;

import analyzer.ast.*;
import com.sun.javafx.geom.Edge;
import com.sun.org.apache.bcel.internal.generic.RET;
import com.sun.org.apache.xpath.internal.NodeSet;
import com.sun.org.apache.xpath.internal.operations.Bool;
import javafx.util.Pair;

import javax.crypto.Mac;
import java.io.PrintWriter;
import java.util.*;

public class PrintMachineCodeVisitor implements ParserVisitor {

    private PrintWriter m_writer = null;

    private Integer REG = 256; // default register limitation
    private ArrayList<String> RETURNED = new ArrayList<String>(); // returned variables from the return statement
    private ArrayList<MachLine> CODE   = new ArrayList<MachLine>(); // representation of the Machine Code in Machine lines (MachLine)
    private ArrayList<String> LOADED   = new ArrayList<String>(); // could be use to keep which variable/pointer are loaded/ defined while going through the intermediate code
    private ArrayList<String> MODIFIED = new ArrayList<String>(); // could be use to keep which variable/pointer are modified while going through the intermediate code

    // Nos ajouts
    private HashMap<String, ArrayList<String>> grapheInterference = new HashMap<>();

    private HashMap<String,String> OP; // map to get the operation name from it's value
    public PrintMachineCodeVisitor(PrintWriter writer) {
        m_writer = writer;

        OP = new HashMap<>();
        OP.put("+", "ADD");
        OP.put("-", "MIN");
        OP.put("*", "MUL");
        OP.put("/", "DIV");


    }

    @Override
    public Object visit(SimpleNode node, Object data) {
        return null;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        // Visiter les enfants
        node.childrenAccept(this, null);

        compute_LifeVar(); // first Life variables computation (should be recalled when machine code generation)
        compute_NextUse(); // first Next-Use computation (should be recalled when machine code generation)
        compute_machineCode(); // generate the machine code from the CODE array (the CODE array should be transformed
        compute_LifeVar();
        compute_NextUse();

        for (int i = 0; i < CODE.size(); i++) // print the output
            m_writer.println(CODE.get(i));
        return null;
    }


    @Override
    public Object visit(ASTNumberRegister node, Object data) {
        REG = ((ASTIntValue) node.jjtGetChild(0)).getValue(); // get the limitation of register
        return null;
    }

    @Override
    public Object visit(ASTReturnStmt node, Object data) {
        for(int i = 0; i < node.jjtGetNumChildren(); i++) {
            RETURNED.add("@" + ((ASTIdentifier) node.jjtGetChild(i)).getValue()); // returned values (here are saved in "@*somthing*" format, you can change that if you want.

            // TODO: the returned variables should be added to the Life_OUT set of the last statement of the basic block (before the "ST" expressions in the machine code)
        }

        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        node.childrenAccept(this, null);
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, null);
        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        // On ne visite pas les enfants puisque l'on va manuellement chercher leurs valeurs
        // On n'a rien a transférer aux enfants
        String assigned = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left     = (String) node.jjtGetChild(1).jjtAccept(this, null);
        String right    = (String) node.jjtGetChild(2).jjtAccept(this, null);
        String op       = node.getOp();

        // TODO: Modify CODE to add the needed MachLine.
        //       here the type of Assignment is "assigned = left op right" and you should put pointers in the MachLine at
        //       the moment (ex: "@a")

        List<String> newList = new ArrayList<>();
        if (!LOADED.contains(left) && !left.contains("#")) {
            newList.add("LD " + left + ", " + left.replace("@", ""));
            MachLine machLine = new MachLine(newList);
            machLine.DEF.add(left);
            CODE.add(machLine);
            LOADED.add(left);
        }
        newList = new ArrayList<>();
        if (!LOADED.contains(right) && !right.contains("#")) {
            newList.add("LD " + right + ", " + right.replace("@", ""));
            MachLine machLine = new MachLine(newList);
            machLine.DEF.add(right);
            CODE.add(machLine);
            LOADED.add(right);
        }
        newList = new ArrayList<>();
        newList.add(OP.get(op) + " " + assigned + ", " + left + ", " + right);
        if (!LOADED.contains(assigned)) {
            LOADED.add(assigned);
        }
        if (!MODIFIED.contains(assigned)) {
            MODIFIED.add(assigned);
        }

        MachLine machLine = new MachLine(newList);
        machLine.DEF.add(assigned);
        machLine.REF.add(right);
        machLine.REF.add(left);
        CODE.add(machLine);
        return null;
    }

    @Override
    public Object visit(ASTAssignUnaryStmt node, Object data) {
        // On ne visite pas les enfants puisque l'on va manuellement chercher leurs valeurs
        // On n'a rien a transférer aux enfants
        String assigned = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left = (String) node.jjtGetChild(1).jjtAccept(this, null);

        // TODO: Modify CODE to add the needed MachLine.
        //       here the type of Assignment is "assigned = - left" and you should put pointers in the MachLine at
        //       the moment (ex: "@a")

        List<String> newList = new ArrayList<>();
        if (!LOADED.contains(left) && !left.contains("#")) {
            newList.add("LD " + left + ", " + left.replace("@", ""));
            MachLine machLine = new MachLine(newList);
            machLine.DEF.add(left);
            CODE.add(machLine);
            LOADED.add(left);
        }
        newList = new ArrayList<>();
        newList.add("ADD " + assigned + ", #0, " + left);
        if (!LOADED.contains(assigned)) {
            LOADED.add(assigned);
        }
        if (!MODIFIED.contains(assigned)) {
            MODIFIED.add(assigned);
        }

        MachLine machLine = new MachLine(newList);
        machLine.DEF.add(assigned);
        machLine.REF.add(left);
        CODE.add(machLine);
        return null;
    }

    @Override
    public Object visit(ASTAssignDirectStmt node, Object data) {
        // On ne visite pas les enfants puisque l'on va manuellement chercher leurs valeurs
        // On n'a rien a transférer aux enfants
        String assigned = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left = (String) node.jjtGetChild(1).jjtAccept(this, null);

        // TODO: Modify CODE to add the needed MachLine.
        //       here the type of Assignment is "assigned = left" and you should put pointers in the MachLine at
        //       the moment (ex: "@a")

        List<String> newList = new ArrayList<>();
        if (!LOADED.contains(left) && !left.contains("#")) {
            newList.add("LD " + left + ", " + left.replace("@", ""));
            MachLine machLine = new MachLine(newList);
            machLine.DEF.add(left);
            CODE.add(machLine);
            LOADED.add(left);
        }
        newList = new ArrayList<>();
        newList.add("ADD " + assigned + ", #0, " + left);
        if (!LOADED.contains(assigned)) {
            LOADED.add(assigned);
        }
        if (!MODIFIED.contains(assigned)) {
            MODIFIED.add(assigned);
        }

        MachLine machLine = new MachLine(newList);
        machLine.DEF.add(assigned);
        machLine.REF.add(left);
        CODE.add(machLine);
        return null;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        //nothing to do here
        return node.jjtGetChild(0).jjtAccept(this, null);
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        //nothing to do here
        return "#"+String.valueOf(node.getValue());
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        //nothing to do here
        return "@" + node.getValue();
    }


    private class NextUse {
        // NextUse class implementation: you can use it or redo it you're way
        public HashMap<String, ArrayList<Integer>> nextuse = new HashMap<String, ArrayList<Integer>>();

        public NextUse() {}

        public NextUse(HashMap<String, ArrayList<Integer>> nextuse) {
            this.nextuse = nextuse;
        }

        public void add(String s, int i) {
            if (!nextuse.containsKey(s)) {
                nextuse.put(s, new ArrayList<Integer>());
            }
            nextuse.get(s).add(i);
        }
        public String toString() {
            String buff = "";
            boolean first = true;
            for (String k : set_ordered(nextuse.keySet())) {
                if (! first) {
                    buff +=", ";
                }
                buff += k + ":" + nextuse.get(k);
                first = false;
            }
            return buff;
        }

        public Object clone() {
            return new NextUse((HashMap<String, ArrayList<Integer>>) nextuse.clone());
        }
    }


    private class MachLine {
        List<String> line;
        public HashSet<String> REF = new HashSet<String>();
        public HashSet<String> DEF = new HashSet<String>();
        public HashSet<Integer> SUCC  = new HashSet<Integer>();
        public HashSet<Integer> PRED  = new HashSet<Integer>();
        public HashSet<String> Life_IN  = new HashSet<String>();
        public HashSet<String> Life_OUT = new HashSet<String>();

        public NextUse Next_IN  = new NextUse();
        public NextUse Next_OUT = new NextUse();

        public MachLine(List<String> s) {
            this.line = s;
            int size = CODE.size();

            // PRED, SUCC already computed (cadeau)
            if (size > 0) {
                PRED.add(size-1);
                CODE.get(size-1).SUCC.add(size);
            }
        }

        public String toString() {
            String buff = "";

            // print line :
            buff += line.get(0);
            if (line.size() > 1) {
                buff += " " + line.get(1);
            }
            for (int i = 2; i < line.size(); i++)
                buff += " " + line.get(i);
            buff +="\n";
            // you can uncomment the others set if you want to see them.
            // buff += "// REF      : " +  REF.toString() +"\n";
            // buff += "// DEF      : " +  DEF.toString() +"\n";
            // buff += "// PRED     : " +  PRED.toString() +"\n";
            // buff += "// SUCC     : " +  SUCC.toString() +"\n";
            buff += "// Life_IN  : " +  Life_IN.toString() +"\n";
            buff += "// Life_OUT : " +  Life_OUT.toString() +"\n";
            buff += "// Next_IN  : " +  Next_IN.toString() +"\n";
            buff += "// Next_OUT : " +  Next_OUT.toString() +"\n";
            return buff;
        }
    }

    private void compute_LifeVar() {
        // TODO: Implement LifeVariable algorithm on the CODE array (for machine code)
        Stack<MachLine> workList = new Stack<>();
        workList.push(CODE.get(CODE.size() - 1));
        while (!workList.empty()) {
            MachLine node = workList.pop();
            for (Integer succ : node.SUCC) {
                MachLine succNode = CODE.get(succ);
                node.Life_OUT.addAll(succNode.Life_IN);
            }
            HashSet<String> oldIn = (HashSet<String>) node.Life_IN.clone();
            node.Life_IN.addAll(node.Life_OUT);
            for (String def : node.DEF) {
                node.Life_IN.remove(def);
            }
            node.Life_IN.addAll(node.REF);

            if (!node.Life_IN.equals(oldIn)) {
                for (Integer pred : node.PRED) {
                    workList.push(CODE.get(pred));
                }
            }
        }
    }

    private void compute_NextUse() {
        // TODO: Implement NextUse algorithm on the CODE array (for machine code)
        Stack<MachLine> workList = new Stack<>();
        workList.push(CODE.get(CODE.size() - 1));
        while (!workList.empty()) {
            MachLine node = workList.pop();
            Integer current_line_number = CODE.indexOf(node);
            for (Integer succ : node.SUCC) {
                MachLine succNode = CODE.get(succ);
                node.Next_OUT.nextuse.putAll(succNode.Next_IN.nextuse);
            }
            NextUse oldIn = (NextUse) node.Next_IN.clone();
            for (Map.Entry<String, ArrayList<Integer>> out : node.Next_OUT.nextuse.entrySet()) {
                if (!node.DEF.contains(out.getKey())) {
                    node.Next_IN.nextuse.put(out.getKey(), out.getValue());
                }
            }

            for (String ref : node.REF) {
                ArrayList<Integer> lineList = new ArrayList<Integer>();
                lineList.add(current_line_number);
                node.Next_IN.nextuse.put(ref, lineList);
            }

            // ignore OLD_IN != IN
            for (Integer pred : node.PRED) {
                workList.push(CODE.get(pred));
            }

        }
    }

    public void compute_machineCode() {
        // TODO: Implement machine code with graph coloring for register assignation (REG is the register limitation)
        //       The pointers (ex: "@a") here should be replace by registers (ex: R0) respecting the coloring algorithm
        //       described in the TP requirements.
    }

    public void compute_graph() {
        for (MachLine line : CODE) {
            for (String node : line.Next_OUT.nextuse.keySet()) {
                Set<String> neighbours = new HashSet<>();
                neighbours.addAll(line.Next_OUT.nextuse.keySet());
                ArrayList<String> prevNeighbours = grapheInterference.get(node);
                neighbours.addAll(prevNeighbours);
                neighbours.remove(node);
                prevNeighbours.clear();
                prevNeighbours.addAll(neighbours);
                grapheInterference.put(node, prevNeighbours);
            }
        }
    }

    public void colourGraph() {
        Stack<Pair<String, ArrayList<String>>> stack = new Stack<>();
        HashMap<String, String> colourMap = new HashMap<>();
        Integer reg = REG;
        while (!grapheInterference.isEmpty()) {
            // Init du noeud ayant le plus de voisins inférieur au REG
            Pair<String, ArrayList<String>> mostNeighboursNode = new Pair<>("", new ArrayList<>());

            // Recherche du noeud

            for (Map.Entry<String, ArrayList<String>> node : grapheInterference.entrySet()) {
                int nbNeighbours = node.getValue().size();
                if (nbNeighbours > mostNeighboursNode.getValue().size() && nbNeighbours < reg) {
                    mostNeighboursNode = new Pair<>(node.getKey(), node.getValue());
                    break;
                }
            }

            if (mostNeighboursNode.getKey().equals("")) {
                //do_spill(grapheInterference.entrySet());
                return;
            } else {
                // Supprimer noeud si trouvé
                grapheInterference.remove(mostNeighboursNode.getKey());
            }
            stack.push(mostNeighboursNode);
        }

        while (!stack.empty()) {
            Pair<String, ArrayList<String>> node = stack.pop();

            grapheInterference.put(node.getKey(), node.getValue());
            int colourCount = 0;
            String colour = "R".concat(String.valueOf(colourCount));
            for (String neighbour : node.getValue()) {
                if (colourMap.get(neighbour).equals(colour)) {
                    colourCount++;
                    colour = "R".concat(String.valueOf(colourCount));
                }
            }
            colourMap.put(node.getKey(), colour);
        }
    }

    public void do_spill(MachLine node) {
        int first = 1;

        for(MachLine machLine :CODE){
            if (machLine.Life_IN.contains(node) && machLine.line.get(0).equals("OP")){
                break;
            }
            first++;
        }
        if (MODIFIED.contains(node)){
            List<String> newList = new ArrayList<>();
            newList.add("ST" + node);
            MachLine machLine = new MachLine(newList);
            CODE.add(first+1,machLine);
        }

        if (!CODE.get(first).Next_OUT.nextuse.get(node).isEmpty()){
            List<String> newList = new ArrayList<>();
            newList.add("LD" + node+"!");
            CODE.add(node.Next_OUT.nextuse.get(0).get(0), new MachLine(newList));

            for (int i = node.Next_OUT.nextuse.get(0).get(0); i< CODE.size(); i++){
                if (false){
                    CODE.remove(i);
                }
                else if (CODE.contains(node)){
                    List<String> otherList = new ArrayList<>();
                    otherList.add( node+"!");
                    CODE.set(CODE.indexOf(node), new MachLine(otherList));
                }

            }


        }
    }

    public List<String> set_ordered(Set<String> s) {
        // function given to order a set in alphabetic order TODO: use it! or redo-it yourself
        List<String> list = new ArrayList<String>(s);
        Collections.sort(list);
        return list;
    }

    // TODO: add any class you judge necessary, and explain them in the report. GOOD LUCK!
}
