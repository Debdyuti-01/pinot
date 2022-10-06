/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.core.common.datatable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.pinot.common.datatable.DataTable;
import org.apache.pinot.common.datatable.DataTableFactory;
import org.apache.pinot.common.datatable.DataTableImplV2;
import org.apache.pinot.common.datatable.DataTableImplV3;
import org.apache.pinot.common.datatable.DataTableImplV4;
import org.apache.pinot.common.request.context.ExpressionContext;
import org.apache.pinot.common.utils.DataSchema;
import org.apache.pinot.common.utils.DataSchema.ColumnDataType;
import org.apache.pinot.core.query.aggregation.function.AggregationFunction;
import org.apache.pinot.core.query.aggregation.function.DistinctAggregationFunction;
import org.apache.pinot.core.query.distinct.DistinctTable;
import org.apache.pinot.core.query.request.context.QueryContext;
import org.apache.pinot.core.query.request.context.utils.QueryContextUtils;


/**
 * The <code>DataTableUtils</code> class provides utility methods for data table.
 */
@SuppressWarnings("rawtypes")
public class DataTableBuilderUtils {
  private DataTableBuilderUtils() {
  }

  /**
   * Returns an empty data table without data.
   */
  public static DataTable getEmptyDataTable() {
    int version = DataTableBuilderFactory.getDataTableVersion();
    switch (version) {
      case DataTableFactory.VERSION_2:
        return new DataTableImplV2();
      case DataTableFactory.VERSION_3:
        return new DataTableImplV3();
      case DataTableFactory.VERSION_4:
        return new DataTableImplV4();
      default:
        throw new IllegalStateException("Unsupported data table version: " + version);
    }
  }

  /**
   * Builds an empty data table based on the broker request.
   */
  public static DataTable buildEmptyDataTable(QueryContext queryContext)
      throws IOException {
    if (QueryContextUtils.isSelectionQuery(queryContext)) {
      return buildEmptyDataTableForSelectionQuery(queryContext);
    } else if (QueryContextUtils.isAggregationQuery(queryContext)) {
      return buildEmptyDataTableForAggregationQuery(queryContext);
    } else {
      assert QueryContextUtils.isDistinctQuery(queryContext);
      return buildEmptyDataTableForDistinctQuery(queryContext);
    }
  }

  /**
   * Helper method to build an empty data table for selection query.
   */
  private static DataTable buildEmptyDataTableForSelectionQuery(QueryContext queryContext) {
    List<ExpressionContext> selectExpressions = queryContext.getSelectExpressions();
    int numSelectExpressions = selectExpressions.size();
    String[] columnNames = new String[numSelectExpressions];
    for (int i = 0; i < numSelectExpressions; i++) {
      columnNames[i] = selectExpressions.get(i).toString();
    }
    ColumnDataType[] columnDataTypes = new ColumnDataType[numSelectExpressions];
    // NOTE: Use STRING column data type as default for selection query
    Arrays.fill(columnDataTypes, ColumnDataType.STRING);
    DataSchema dataSchema = new DataSchema(columnNames, columnDataTypes);
    return DataTableBuilderFactory.getDataTableBuilder(dataSchema).build();
  }

  /**
   * Helper method to build an empty data table for aggregation query.
   */
  private static DataTable buildEmptyDataTableForAggregationQuery(QueryContext queryContext)
      throws IOException {
    AggregationFunction[] aggregationFunctions = queryContext.getAggregationFunctions();
    assert aggregationFunctions != null;
    int numAggregations = aggregationFunctions.length;
    List<ExpressionContext> groupByExpressions = queryContext.getGroupByExpressions();
    if (groupByExpressions != null) {
      // Aggregation group-by query

      int numColumns = groupByExpressions.size() + numAggregations;
      String[] columnNames = new String[numColumns];
      ColumnDataType[] columnDataTypes = new ColumnDataType[numColumns];
      int index = 0;
      for (ExpressionContext groupByExpression : groupByExpressions) {
        columnNames[index] = groupByExpression.toString();
        // Use STRING column data type as default for group-by expressions
        columnDataTypes[index] = ColumnDataType.STRING;
        index++;
      }
      for (AggregationFunction aggregationFunction : aggregationFunctions) {
        // NOTE: Use AggregationFunction.getResultColumnName() for SQL format response
        columnNames[index] = aggregationFunction.getResultColumnName();
        columnDataTypes[index] = aggregationFunction.getIntermediateResultColumnType();
        index++;
      }
      return DataTableBuilderFactory.getDataTableBuilder(new DataSchema(columnNames, columnDataTypes)).build();
    } else {
      // Aggregation only query

      String[] aggregationColumnNames = new String[numAggregations];
      ColumnDataType[] columnDataTypes = new ColumnDataType[numAggregations];
      Object[] aggregationResults = new Object[numAggregations];
      for (int i = 0; i < numAggregations; i++) {
        AggregationFunction aggregationFunction = aggregationFunctions[i];
        // NOTE: For backward-compatibility, use AggregationFunction.getColumnName() for aggregation only query
        aggregationColumnNames[i] = aggregationFunction.getColumnName();
        columnDataTypes[i] = aggregationFunction.getIntermediateResultColumnType();
        aggregationResults[i] =
            aggregationFunction.extractAggregationResult(aggregationFunction.createAggregationResultHolder());
      }

      // Build the data table
      DataTableBuilder dataTableBuilder =
          DataTableBuilderFactory.getDataTableBuilder(new DataSchema(aggregationColumnNames, columnDataTypes));
      dataTableBuilder.startRow();
      for (int i = 0; i < numAggregations; i++) {
        switch (columnDataTypes[i]) {
          case LONG:
            dataTableBuilder.setColumn(i, ((Number) aggregationResults[i]).longValue());
            break;
          case DOUBLE:
            dataTableBuilder.setColumn(i, ((Double) aggregationResults[i]).doubleValue());
            break;
          case OBJECT:
            dataTableBuilder.setColumn(i, aggregationResults[i]);
            break;
          default:
            throw new UnsupportedOperationException(
                "Unsupported aggregation column data type: " + columnDataTypes[i] + " for column: "
                    + aggregationColumnNames[i]);
        }
      }
      dataTableBuilder.finishRow();
      return dataTableBuilder.build();
    }
  }

  /**
   * Helper method to build an empty data table for distinct query.
   */
  private static DataTable buildEmptyDataTableForDistinctQuery(QueryContext queryContext)
      throws IOException {
    AggregationFunction[] aggregationFunctions = queryContext.getAggregationFunctions();
    assert aggregationFunctions != null && aggregationFunctions.length == 1
        && aggregationFunctions[0] instanceof DistinctAggregationFunction;
    DistinctAggregationFunction distinctAggregationFunction = (DistinctAggregationFunction) aggregationFunctions[0];

    // Create the distinct table
    String[] columnNames = distinctAggregationFunction.getColumns();
    ColumnDataType[] columnDataTypes = new ColumnDataType[columnNames.length];
    // NOTE: Use STRING column data type as default for distinct query
    Arrays.fill(columnDataTypes, ColumnDataType.STRING);
    DistinctTable distinctTable =
        new DistinctTable(new DataSchema(columnNames, columnDataTypes), Collections.emptySet(),
            queryContext.isNullHandlingEnabled());

    // Build the data table
    DataTableBuilder dataTableBuilder = DataTableBuilderFactory.getDataTableBuilder(
        new DataSchema(new String[]{distinctAggregationFunction.getColumnName()},
            new ColumnDataType[]{ColumnDataType.OBJECT}));
    dataTableBuilder.startRow();
    dataTableBuilder.setColumn(0, distinctTable);
    dataTableBuilder.finishRow();
    return dataTableBuilder.build();
  }
}