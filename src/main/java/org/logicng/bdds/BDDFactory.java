///////////////////////////////////////////////////////////////////////////
//                   __                _      _   ________               //
//                  / /   ____  ____ _(_)____/ | / / ____/               //
//                 / /   / __ \/ __ `/ / ___/  |/ / / __                 //
//                / /___/ /_/ / /_/ / / /__/ /|  / /_/ /                 //
//               /_____/\____/\__, /_/\___/_/ |_/\____/                  //
//                           /____/                                      //
//                                                                       //
//               The Next Generation Logic Library                       //
//                                                                       //
///////////////////////////////////////////////////////////////////////////
//                                                                       //
//  Copyright 2015-2016 Christoph Zengler                                //
//                                                                       //
//  Licensed under the Apache License, Version 2.0 (the "License");      //
//  you may not use this file except in compliance with the License.     //
//  You may obtain a copy of the License at                              //
//                                                                       //
//  http://www.apache.org/licenses/LICENSE-2.0                           //
//                                                                       //
//  Unless required by applicable law or agreed to in writing, software  //
//  distributed under the License is distributed on an "AS IS" BASIS,    //
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or      //
//  implied.  See the License for the specific language governing        //
//  permissions and limitations under the License.                       //
//                                                                       //
///////////////////////////////////////////////////////////////////////////

/*========================================================================
           Copyright (C) 1996-2002 by Jorn Lind-Nielsen
                        All rights reserved

Permission is hereby granted, without written agreement and without
license or royalty fees, to use, reproduce, prepare derivative
works, distribute, and display this software and its documentation
for any purpose, provided that (1) the above copyright notice and
the following two paragraphs appear in all copies of the source code
and (2) redistributions, including without limitation binaries,
reproduce these notices in the supporting documentation. Substantial
modifications to this software may be copyrighted by their authors
and need not follow the licensing terms described here, provided
that the new terms are clearly indicated in all files where they apply.

IN NO EVENT SHALL JORN LIND-NIELSEN, OR DISTRIBUTORS OF THIS
SOFTWARE BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT, SPECIAL,
INCIDENTAL, OR CONSEQUENTIAL DAMAGES ARISING OUT OF THE USE OF THIS
SOFTWARE AND ITS DOCUMENTATION, EVEN IF THE AUTHORS OR ANY OF THE
ABOVE PARTIES HAVE BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

JORN LIND-NIELSEN SPECIFICALLY DISCLAIM ANY WARRANTIES, INCLUDING,
BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE PROVIDED HEREUNDER IS
ON AN "AS IS" BASIS, AND THE AUTHORS AND DISTRIBUTORS HAVE NO
OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
MODIFICATIONS.
========================================================================*/

package org.logicng.bdds;

import org.logicng.bdds.datastructures.BDD;
import org.logicng.bdds.datastructures.BDDConstant;
import org.logicng.bdds.datastructures.BDDInnerNode;
import org.logicng.bdds.datastructures.BDDNode;
import org.logicng.bdds.jbuddy.BDDKernel;
import org.logicng.collections.LNGVector;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Equivalence;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.formulas.Implication;
import org.logicng.formulas.Literal;
import org.logicng.formulas.Not;
import org.logicng.formulas.Variable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.logicng.formulas.FType.AND;

/**
 * The factory for the jBuddy implementation.
 * @version 1.4
 * @since 1.4
 */
public final class BDDFactory {

  private final BDDKernel kernel;

  private final FormulaFactory f;
  private SortedMap<Variable, Integer> var2idx;
  private SortedMap<Integer, Variable> idx2var;


  /**
   * Constructs a new jBuddy BDD factory.
   * @param numNodes  the initial number of nodes in the BDD
   * @param cacheSize the cache size
   * @param f         the formula factory
   */
  public BDDFactory(int numNodes, int cacheSize, final FormulaFactory f) {
    this.f = f;
    this.kernel = new BDDKernel(numNodes, cacheSize);
    this.var2idx = new TreeMap<>();
    this.idx2var = new TreeMap<>();
  }

  /**
   * Builds a BDD for a given formula.  BDDs support all Boolean formula types but not pseudo-Boolean constraints.
   * The reason is that before converting a formula to a BDD one must specify the number of variables.  In case of
   * pseudo-Boolean constraints this number depends on the translation of the constraint.  Therefore the caller first
   * has to transform any pseudo-Boolean constraints in their respective CNF representation before converting them
   * to a BDD.
   * @param formula the formula
   * @return the top node of the BDD
   */
  public BDD build(final Formula formula) {
    return new BDD(buildRec(formula), this);
  }

  /**
   * Recursive build procedure for the BDD.
   * @param formula the formula
   * @return the BDD index
   */
  private int buildRec(final Formula formula) {
    switch (formula.type()) {
      case FALSE:
        return BDDKernel.BDD_FALSE;
      case TRUE:
        return BDDKernel.BDD_TRUE;
      case LITERAL:
        final Literal lit = (Literal) formula;
        Integer idx = var2idx.get(lit.variable());
        if (idx == null) {
          idx = var2idx.size();
          var2idx.put(lit.variable(), idx);
          idx2var.put(idx, lit.variable());
        }
        return lit.phase() ? kernel.ithVar(idx) : kernel.nithVar(idx);
      case NOT:
        final Not not = (Not) formula;
        return kernel.addRef(kernel.not(buildRec(not.operand())));
      case IMPL:
        final Implication impl = (Implication) formula;
        return kernel.addRef(kernel.implication(buildRec(impl.left()), buildRec(impl.right())));
      case EQUIV:
        final Equivalence equiv = (Equivalence) formula;
        return kernel.addRef(kernel.equivalence(buildRec(equiv.left()), buildRec(equiv.right())));
      case AND:
      case OR:
        final Iterator<Formula> it = formula.iterator();
        int res = buildRec(it.next());
        while (it.hasNext())
          res = formula.type() == AND
                  ? kernel.addRef(kernel.and(res, buildRec(it.next())))
                  : kernel.addRef(kernel.or(res, buildRec(it.next())));
        return res;
      default:
        throw new IllegalArgumentException("Unsupported operator for BDD generation: " + formula.type());
    }
  }

  /**
   * Sets the variable order for this BDD.  This method must be called BEFORE the BDD is generated.
   * @param varOrder the variable order
   */
  public void setVariableOrder(final List<Variable> varOrder) {
    this.setVariableOrder(varOrder.toArray(new Variable[varOrder.size()]));
  }

  /**
   * Sets the variable order for this BDD.  This method must be called BEFORE the BDD is generated.
   * @param varOrder the variable order
   */
  public void setVariableOrder(final LNGVector<Variable> varOrder) {
    this.setVariableOrder(varOrder.toArray());
  }

  /**
   * Sets the variable order for this factory manually.  In this case the number of variables has not to be set manually.
   * @param varOrder the variable order.
   */
  public void setVariableOrder(final Variable... varOrder) {
    kernel.setNumberOfVars(varOrder.length);
    for (final Variable lit : varOrder) {
      int idx = var2idx.size();
      var2idx.put(lit.variable(), idx);
      idx2var.put(idx, lit.variable());
    }
  }

  /**
   * Returns a BDD representing the i-th variable (one node with the children true and false).
   * @param i the index i
   * @return the BDD representing the i-th variable
   * @throws IllegalArgumentException if the index is not within the range of variables
   */
  public int ithVar(int i) {
    return kernel.addRef(kernel.ithVar(i));
  }

  /**
   * Returns a BDD representing the negation of the i-th variable (one node with the children true and false).
   * @param i the index i
   * @return the BDD representing the negated i-th variable
   * @throws IllegalArgumentException if the index is not within the range of variables
   */
  public int nithVar(int i) {
    return kernel.addRef(kernel.nithVar(i));
  }

  /**
   * Returns the conjunction of two BDDs.
   * @param a the first BDD
   * @param b the second BDD
   * @return the conjunction of the two BDDs
   */
  public int and(int a, int b) {
    return kernel.addRef(kernel.and(a, b));
  }

  /**
   * Returns the disjunction of two BDDs.
   * @param a the first BDD
   * @param b the second BDD
   * @return the disjunction of the two BDDs
   */
  public int or(int a, int b) {
    return kernel.addRef(kernel.or(a, b));
  }

  /**
   * Returns the implication of two BDDs.
   * @param a the first BDD
   * @param b the second BDD
   * @return the implication of the two BDDs
   */
  public int implication(int a, int b) {
    return kernel.addRef(kernel.implication(a, b));
  }

  /**
   * Returns the equivalence of two BDDs.
   * @param a the first BDD
   * @param b the second BDD
   * @return the equivalence of the two BDDs
   */
  public int equivalence(int a, int b) {
    return kernel.addRef(kernel.equivalence(a, b));
  }

  /**
   * Returns the negation of a BDD.
   * @param r the BDD
   * @return the negation of the BDD
   */
  public int not(int r) {
    return kernel.addRef(kernel.not(r));
  }

  /**
   * Returns a BDD representing a variable set.  The BDD variable set is represented as the conjunction of all the
   * variables in their positive form and may just as well be made that way by the user. The user should keep a
   * reference to the returned BDD instead of building it every time the set is needed.
   * @param varset the set of variable indices
   * @return the BDD representing the variable set
   */
  public int makeSet(int[] varset) {
    return kernel.addRef(kernel.makeSet(varset));
  }

  /**
   * Returns the number of paths to the terminal node 'one'.
   * @param r the BDD
   * @return the number of paths to the terminal node 'one'
   */
  public BigDecimal pathCountOne(int r) {
    return kernel.pathCountOne(r);
  }

  /**
   * Returns the number of paths to the terminal node 'zero'.
   * @param r the BDD
   * @return the number of paths to the terminal node 'zero'
   */
  public BigDecimal pathCountZero(int r) {
    return kernel.pathCountZero(r);
  }

  /**
   * Sets the number of variables for this factory.
   * @param num the number of variables
   */
  public void setNumberOfVars(int num) {
    kernel.setNumberOfVars(num);
  }

  /**
   * Returns whether BDD m is a tautology.
   * @param m the BDD to test for the tautology test (index of root node)
   * @return {@code true} if m is a tautology, {@code false} else
   */
  public boolean isTautology(final BDD m) {
    return m.index() == 1;
  }

  /**
   * Returns whether BDD m is a contradiction.
   * @param m the BDD to test for the contradiction test (index of root node)
   * @return {@code true} if m is a contradiction, {@code false} else
   */
  public boolean isContradiction(final BDD m) {
    return m.index() == 0;
  }

  /**
   * Returns the variable index labeling the given root node.
   * @param root the root node of the BDD
   * @return the variable index
   */
  public int bddVar(int root) {
    return kernel.bddVar(root);
  }

  public Variable variableAtNode(int node) {
    return this.idx2var.get(kernel.bddVar(node));
  }

  public int lowNode(int root) {
    return kernel.bddLow(root);
  }

  public int highNode(int root) {
    return kernel.bddHigh(root);
  }

  /**
   * Returns the number of variables.
   * @return the number of variables
   */
  int numberOfVars() {
    return kernel.numberOfVars();
  }

  /**
   * Increases the number of variables to use by the given number.
   * @param num the number of new variables
   * @throws IllegalArgumentException if the number of new variables is not legal (negative or too big)
   */
  public void extendVarNum(int num) {
    kernel.extendVarNum(num);
  }

  /**
   * Returns the model count of a given BDD.
   * @param bdd the BDD
   * @return the model count
   */
  public BigDecimal modelCount(final BDD bdd) {
    return kernel.satCount(bdd.index());
  }

  /**
   * Returns the model count of a given BDD with a given number of unimportant variables.
   * @param bdd             the BDD
   * @param unimportantVars the number of unimportant variables
   * @return the model count
   */
  public BigDecimal modelCount(final BDD bdd, int unimportantVars) {
    return modelCount(bdd).divide(BigDecimal.valueOf((int) Math.pow(2, unimportantVars)));
  }

  /**
   * Enumerates all models of a given BDD.
   * @param bdd the BDD
   * @return the list of all models
   */
  public List<Assignment> enumerateAllModels(final BDD bdd) {
    return enumerateAllModels(bdd, (Collection<Variable>) null);
  }

  /**
   * Enumerates all models of a given BDD wrt. a given set of variables.
   * @param bdd       the BDD
   * @param variables the variables
   * @return the list of all models
   */
  public List<Assignment> enumerateAllModels(final BDD bdd, final Variable[] variables) {
    return this.enumerateAllModels(bdd, Arrays.asList(variables));
  }

  /**
   * Enumerates all models of a given BDD wrt. a given set of variables.
   * @param bdd       the BDD
   * @param variables the variables
   * @return the list of all models
   */
  public List<Assignment> enumerateAllModels(final BDD bdd, final Collection<Variable> variables) {
    final Set<Assignment> res = new HashSet<>();
    final List<byte[]> models = kernel.allSat(bdd.index());
    SortedSet<Integer> temp;
    if (variables == null)
      temp = new TreeSet<>(this.var2idx.values());
    else {
      temp = new TreeSet<>();
      for (final Map.Entry<Variable, Integer> e : this.var2idx.entrySet())
        if (variables.contains(e.getKey()))
          temp.add(e.getValue());
    }
    final int[] relevantIndices = new int[temp.size()];
    int count = 0;
    for (final Integer i : temp)
      relevantIndices[count++] = i;
    for (final byte[] model : models) {
      final List<Assignment> allAssignments = new LinkedList<>();
      generateAllModels(allAssignments, model, relevantIndices, 0);
      res.addAll(allAssignments);
    }
    return new ArrayList<>(res);
  }

  private void generateAllModels(final List<Assignment> assignments, byte[] model, final int[] relevantIndices, int position) {
    if (position == relevantIndices.length) {
      final Assignment assignment = new Assignment();
      for (final int i : relevantIndices)
        if (model[i] == 0)
          assignment.addLiteral(idx2var.get(i).negate());
        else
          assignment.addLiteral(idx2var.get(i));
      assignments.add(assignment);
    } else if (model[relevantIndices[position]] != -1)
      generateAllModels(assignments, model, relevantIndices, position + 1);
    else {
      model[relevantIndices[position]] = 0;
      generateAllModels(assignments, model, relevantIndices, position + 1);
      model[relevantIndices[position]] = 1;
      generateAllModels(assignments, model, relevantIndices, position + 1);
      model[relevantIndices[position]] = -1;
    }
  }

  /**
   * Returns a CNF formula for a given BDD.
   * @param bdd the node
   * @return the CNF for the formula represented by the BDD
   */
  public Formula cnf(final BDD bdd) {
    final List<byte[]> unsatPaths = kernel.allUnsat(bdd.index());
    final List<Formula> clauses = new LinkedList<>();
    List<Formula> literals;
    for (final byte[] path : unsatPaths) {
      literals = new LinkedList<>();
      for (int i = 0; i < path.length; i++)
        if (path[i] == 0)
          literals.add(this.idx2var.get(i));
        else if (path[i] == 1)
          literals.add(this.idx2var.get(i).negate());
      clauses.add(f.or(literals));
    }
    return f.and(clauses);
  }

  /**
   * Returns the number of clauses for the CNF formula of the given BDD.
   * @param bdd the node
   * @return the number of clauses for the CNF formula of the given BDD
   */
  public BigDecimal numberOfClausesCNF(final BDD bdd) {
    return kernel.pathCountZero(bdd.index());
  }

  /**
   * Returns a DNF formula for a given BDD.
   * @param bdd the BDD
   * @return the DNF for the formula represented by the BDD
   */
  public Formula dnf(final BDD bdd) {
    final List<Formula> ops = new LinkedList<>();
    for (final Assignment ass : this.enumerateAllModels(bdd))
      ops.add(ass.formula(f));
    return ops.isEmpty() ? f.falsum() : f.or(ops);
  }

  /**
   * Restricts the given BDD.
   * @param bdd the BDD
   * @param restriction the restriction
   * @return the restricted BDD
   */
  public BDD restrict(final BDD bdd, final Collection<Literal> restriction) {
    final BDD resBDD = build(f.and(restriction));
    return new BDD(this.kernel.restrict(bdd.index(), resBDD.index()), this);
  }

  /**
   * Returns a LogicNG internal BDD data structure of a given BDD.
   * @param bdd the BDD
   * @return the BDD as LogicNG data structure
   */
  public BDDNode toLngBdd(int bdd) {
    final BDDConstant falseNode = BDDConstant.getFalsumNode(this.f);
    final BDDConstant trueNode = BDDConstant.getVerumNode(this.f);
    if (bdd == BDDKernel.BDD_FALSE)
      return falseNode;
    if (bdd == BDDKernel.BDD_TRUE)
      return trueNode;
    final List<int[]> nodes = this.kernel.allNodes(bdd);
    final Map<Integer, BDDInnerNode> innerNodes = new HashMap<>();
    for (int[] node : nodes) {
      final int nodenum = node[0];
      final Variable variable = this.idx2var.get(node[1]);
      final BDDNode lowNode = getInnerNode(node[2], falseNode, trueNode, innerNodes);
      final BDDNode highNode = getInnerNode(node[3], falseNode, trueNode, innerNodes);
      final BDDInnerNode foundNode = innerNodes.get(nodenum);
      if (foundNode == null)
        innerNodes.put(nodenum, new BDDInnerNode(variable, lowNode, highNode));
      else {
        if (foundNode.label() == null)
          foundNode.setVar(variable);
        if (foundNode.low() == null)
          foundNode.setLow(lowNode);
        if (foundNode.high() == null)
          foundNode.setHigh(highNode);
      }
    }
    return innerNodes.get(bdd);
  }

  /**
   * Returns the internal nodes of a given BDD (for e.g. writing to a DOT file)
   * @param bdd the BDD
   * @return the internal nodes of the BDD
   */
  public List<InternalBDDNode> getInternalNodes(int bdd) {
    List<InternalBDDNode> result = new ArrayList<>();
    for (int[] node : this.kernel.allNodes(bdd))
      result.add(new InternalBDDNode(node[0], this.idx2var.get(node[1]).name(), node[2], node[3]));
    return result;
  }

  private BDDNode getInnerNode(int index, BDDConstant falseNode, BDDConstant trueNode,
                               final Map<Integer, BDDInnerNode> innerNodes) {
    if (index == BDDKernel.BDD_FALSE)
      return falseNode;
    else if (index == BDDKernel.BDD_TRUE)
      return trueNode;
    else {
      final BDDInnerNode innerNode = innerNodes.get(index);
      if (innerNode == null) {
        BDDInnerNode newNode = new BDDInnerNode();
        innerNodes.put(index, newNode);
        return newNode;
      }
      return innerNode;
    }
  }

  /**
   * Returns the number of nodes in the node table that are currently in use. Note that dead nodes that have not been
   * reclaimed yet by a garbage collection are counted as active.
   * @return the number of active nodes
   */
  public int nodenum() {
    return kernel.numberOfNodes();
  }

  /**
   * Returns the number of nodes currently allocated. This includes both dead and active nodes.
   * @return the number of nodes currently allocated
   */
  public int numberOfAllocs() {
    return kernel.numberOfAllocs();
  }

  /**
   * Prints the state of the kernel
   */
  public void printStats() {
    kernel.printStats();
  }

  /**
   * Prints the node table for a given BDD root node.
   * @param r the BDD root node
   */
  void printTable(int r) {
    kernel.printTable(r);
  }

  /**
   * Returns the formula factory for this BDD factory.
   * @return the formula factory
   */
  public FormulaFactory getF() {
    return this.f;
  }

  public static class InternalBDDNode {
    private final int nodenum;
    private final String label;
    private final int low;
    private final int high;

    public InternalBDDNode(int nodenum, String label, int low, int high) {
      this.nodenum = nodenum;
      this.label = label;
      this.low = low;
      this.high = high;
    }

    public int getNodenum() {
      return nodenum;
    }

    public String getLabel() {
      return label;
    }

    public int getLow() {
      return low;
    }

    public int getHigh() {
      return high;
    }
  }
}
