/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.spockframework.compiler;

import org.spockframework.compiler.model.*;

import java.util.*;

import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;

public class AbstractDeepBlockRewriter extends StatementReplacingVisitorSupport {
  private final AstNodeCache nodeCache;
  protected Block block;
  protected Statement currTopLevelStat;
  protected ExpressionStatement currExprStat;
  protected BinaryExpression currBinaryExpr;
  protected MethodCallExpression currMethodCallExpr;
  protected ClosureExpression currClosure;
  protected ISpecialMethodCall currSpecialMethodCall = NoSpecialMethodCall.INSTANCE;
  protected Collection<Statement> pastSpecialMethodCallStats = new HashSet<>();

  // following fields are filled in by subclasses
  protected boolean conditionFound;
  protected boolean deepNonGroupedConditionFound;
  protected boolean groupConditionFound;
  protected boolean interactionFound;
  protected MethodCallExpression foundExceptionCondition;
  protected final List<Statement> thenBlockInteractionStats = new ArrayList<>();

  public AbstractDeepBlockRewriter(Block block, AstNodeCache nodeCache) {
    this.block = block;
    this.nodeCache = nodeCache;
  }

  protected void conditionFound() {
    conditionFound = true;
    groupConditionFound = currSpecialMethodCall.isGroupConditionBlock();
    deepNonGroupedConditionFound |= !groupConditionFound;
  }

  public boolean isDeepNonGroupedConditionFound() {
    return deepNonGroupedConditionFound;
  }

  public boolean isConditionFound() {
    return conditionFound;
  }

  public boolean isGroupConditionFound() {
    return groupConditionFound;
  }

  public boolean isExceptionConditionFound() {
    return foundExceptionCondition != null;
  }

  public List<Statement> getThenBlockInteractionStats() {
    return thenBlockInteractionStats;
  }

  public MethodCallExpression getFoundExceptionCondition() {
    return foundExceptionCondition;
  }

  public void visit(Block block) {
    this.block = block;
    ListIterator<Statement> iterator = block.getAst().listIterator();
    while (iterator.hasNext()) {
      Statement next = iterator.next();
      currTopLevelStat = next;
      Statement replaced = replace(next);
      if (interactionFound && block instanceof ThenBlock) {
        iterator.remove();
        thenBlockInteractionStats.add(replaced);
        interactionFound = false;
      } else {
        iterator.set(replaced);
      }
    }
  }

  @Override
  public final void visitExpressionStatement(ExpressionStatement stat) {
    ExpressionStatement oldExpressionStatement = currExprStat;
    currExprStat = stat;
    try {
      doVisitExpressionStatement(stat);
    } finally {
      currExprStat = oldExpressionStatement;
    }
  }

  @Override
  public final void visitBinaryExpression(BinaryExpression expr) {
    BinaryExpression oldBinaryExpression = currBinaryExpr;
    currBinaryExpr = expr;
    try {
      doVisitBinaryExpression(expr);
    } finally {
      currBinaryExpr = oldBinaryExpression;
    }
  }

  @Override
  public final void visitMethodCallExpression(MethodCallExpression expr) {
    MethodCallExpression oldMethodCallExpr = currMethodCallExpr;
    currMethodCallExpr = expr;

    ISpecialMethodCall oldSpecialMethodCall = currSpecialMethodCall;
    ISpecialMethodCall newSpecialMethodCall = SpecialMethodCall.parse(currMethodCallExpr, currBinaryExpr, nodeCache);
    if (newSpecialMethodCall != null) {
      currSpecialMethodCall = newSpecialMethodCall;
      if (newSpecialMethodCall.isMatch(currExprStat)) {
        pastSpecialMethodCallStats.add(currExprStat);
      }
    }

    try {
      doVisitMethodCallExpression(expr);
    } finally {
      currMethodCallExpr = oldMethodCallExpr;
      currSpecialMethodCall = oldSpecialMethodCall;
    }
  }

  @Override
  public final void visitClosureExpression(ClosureExpression expr) {
    ClosureExpression oldClosure = currClosure;
    currClosure = expr;
    boolean oldConditionFound = conditionFound;
    boolean oldGroupConditionFound = groupConditionFound;
    conditionFound = false; // any closure terminates conditionFound scope
    groupConditionFound = false;
    ISpecialMethodCall oldSpecialMethodCall = currSpecialMethodCall;
    if (!currSpecialMethodCall.isMatch(expr)) {
      currSpecialMethodCall = NoSpecialMethodCall.INSTANCE; // unrelated closure terminates currSpecialMethodCall scope
    }
    try {
      Statement code = expr.getCode();
      // the code of a closure is not necessarily a block statement but could for example
      // also be a single assert statement in cases like `case 3 -> assert 1 == 1`
      // to uniformly treat the closure code as block statement and later be able to
      // add statements to it, wrap non-block statements in a block statement here
      if (!(code instanceof BlockStatement)) {
        BlockStatement block = new BlockStatement();
        block.addStatement(code);
        expr.setCode(block);
      }
      doVisitClosureExpression(expr);
    } finally {
      currClosure = oldClosure;
      conditionFound = oldConditionFound;
      groupConditionFound = oldGroupConditionFound;
      currSpecialMethodCall = oldSpecialMethodCall;
    }
  }

  protected void doVisitExpressionStatement(ExpressionStatement stat) {
    super.visitExpressionStatement(stat);
  }

  protected void doVisitBinaryExpression(BinaryExpression expr) {
    super.visitBinaryExpression(expr);
  }

  protected void doVisitMethodCallExpression(MethodCallExpression expr) {
    super.visitMethodCallExpression(expr);
  }

  protected void doVisitClosureExpression(ClosureExpression expr) {
    super.visitClosureExpression(expr);
  }
}
