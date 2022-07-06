package com.pingcap.tikv.partition;

import com.pingcap.tikv.expression.ColumnRef;
import com.pingcap.tikv.expression.Expression;
import com.pingcap.tikv.expression.FuncCallExpr;
import com.pingcap.tikv.meta.TiPartitionDef;
import com.pingcap.tikv.meta.TiPartitionInfo;
import com.pingcap.tikv.meta.TiTableInfo;
import com.pingcap.tikv.parser.TiParser;
import com.pingcap.tikv.row.Row;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class PartitionedTable {

  private final TableCommon logicalTable;

  private final TableCommon[] physicalTables;

  private final Expression partitionExpr;

  private final TiPartitionInfo partitionInfo;

  public static PartitionedTable newPartitionTable(
      TableCommon logicalTable, TiTableInfo tableInfo) {
    Expression partitionExpr = generatePartitionExpr(tableInfo);

    List<TiPartitionDef> partitionDefs = tableInfo.getPartitionInfo().getDefs();
    TableCommon[] physicalTables = new TableCommon[partitionDefs.size()];
    for (int i = 0; i < partitionDefs.size(); i++) {
      TiPartitionDef tiPartitionDef = partitionDefs.get(i);
      TableCommon temp =
          new TableCommon(
              logicalTable.getLogicalTableId(),
              tiPartitionDef.getId(),
              logicalTable.getTableInfo());
      physicalTables[i] = temp;
    }

    return new PartitionedTable(
        logicalTable, physicalTables, partitionExpr, tableInfo.getPartitionInfo());
  }

  public static Expression generatePartitionExpr(TiTableInfo tableInfo) {
    TiPartitionInfo partitionInfo = tableInfo.getPartitionInfo();
    switch (partitionInfo.getType()) {
      case RangePartition:
        return generateRangePartitionExpr(tableInfo);
      case HashPartition:
        return generateHashPartitionExpr(tableInfo);
      default:
        throw new UnsupportedOperationException(
            String.format("Unsupported partition type %s", partitionInfo.getType()));
    }
  }

  private static Expression generateHashPartitionExpr(TiTableInfo tableInfo) {
    TiParser parser = new TiParser(tableInfo);
    return parser.parseExpression(tableInfo.getPartitionInfo().getExpr());
  }

  private static Expression generateRangePartitionExpr(TiTableInfo tableInfo) {
    TiParser parser = new TiParser(tableInfo);
    return parser.parseExpression(tableInfo.getPartitionInfo().getExpr());
  }

  public TableCommon locatePartition(Row row) {
    switch (partitionInfo.getType()) {
      case RangePartition:
        if (partitionInfo.getColumns().isEmpty()) {
          return locateRangePartition(row);
        } else {
          return locateRangeColumnPartition(row);
        }
      case HashPartition:
        return locateHashPartition(row);
      default:
        throw new UnsupportedOperationException(
            String.format("Unsupported partition type %s", partitionInfo.getType()));
    }
  }

  private TableCommon locateHashPartition(Row row) {
    if (partitionExpr instanceof ColumnRef) {
      ColumnRef columnRef = (ColumnRef) partitionExpr;
      Number id =
          (Number)
              row.get(columnRef.getColumnInfo().getOffset(), columnRef.getColumnInfo().getType());
      int partitionId = (int) (id.longValue() % physicalTables.length);
      return physicalTables[partitionId];
    } else if (partitionExpr instanceof FuncCallExpr) {
      // TODO: support hash partition
      return null;
    } else {
      throw new UnsupportedOperationException(
          String.format("Unsupported partition expr %s", partitionExpr));
    }
  }

  private TableCommon locateRangeColumnPartition(Row data) {
    return null;
  }

  private TableCommon locateRangePartition(Row data) {
    return null;
  }
}
