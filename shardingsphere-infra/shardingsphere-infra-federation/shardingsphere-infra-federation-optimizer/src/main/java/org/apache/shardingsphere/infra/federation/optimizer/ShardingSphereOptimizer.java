/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.infra.federation.optimizer;

import lombok.RequiredArgsConstructor;
import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.shardingsphere.infra.federation.optimizer.converter.SQLNodeConverterEngine;
import org.apache.shardingsphere.sql.parser.sql.common.statement.SQLStatement;

/**
 * ShardingSphere optimizer.
 */
@RequiredArgsConstructor
public final class ShardingSphereOptimizer {
    
    private final SqlToRelConverter converter;
    
    private final RelOptPlanner hepPlannerWithoutCalc;
    
    private final RelOptPlanner hepPlannerWithCalc;
    
    /**
     * Optimize query execution plan.
     * 
     * @param sqlStatement SQL statement
     * @return optimized relational node
     */
    public RelNode optimize(final SQLStatement sqlStatement) {
        SqlNode sqlNode = SQLNodeConverterEngine.convert(sqlStatement);
        RelNode logicPlan = converter.convertQuery(sqlNode, true, true).rel;
        RelNode ruleBasedPlan = optimizeWithRBO(logicPlan, hepPlannerWithoutCalc);
        RelNode costBasedPlan = optimizeWithCBO(ruleBasedPlan, converter);
        return optimizeWithRBO(costBasedPlan, hepPlannerWithCalc);
    }
    
    private static RelNode optimizeWithRBO(final RelNode logicPlan, final RelOptPlanner hepPlanner) {
        hepPlanner.setRoot(logicPlan);
        return hepPlanner.findBestExp();
    }
    
    private RelNode optimizeWithCBO(final RelNode bestPlan, final SqlToRelConverter converter) {
        RelOptPlanner planner = converter.getCluster().getPlanner();
        if (!bestPlan.getTraitSet().equals(converter.getCluster().traitSet().replace(EnumerableConvention.INSTANCE))) {
            planner.setRoot(planner.changeTraits(bestPlan, converter.getCluster().traitSet().replace(EnumerableConvention.INSTANCE)));
        } else {
            planner.setRoot(bestPlan);
        }
        return planner.findBestExp();
    }
}
