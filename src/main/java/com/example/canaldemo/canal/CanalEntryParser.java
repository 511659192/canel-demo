package com.example.canaldemo.canal;

import com.alibaba.otter.canal.protocol.CanalEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 canal 协议的 Entry 解析为面向业务的行变更事件。
 */
@Component
public class CanalEntryParser {

    public List<CanalEventMessage> parse(List<CanalEntry.Entry> entries) {
        List<CanalEventMessage> events = new ArrayList<>();
        for (CanalEntry.Entry entry : entries) {
            // 事务边界事件对业务无意义，跳过
            if (entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONBEGIN || entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONEND) {
                continue;
            }
            CanalEntry.RowChange rowChange;
            try {
                rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            } catch (Exception e) {
                throw new IllegalStateException("解析 canal Entry 失败, logfileName=" + entry.getHeader()
                        .getLogfileName() + ", logOffset=" + entry.getHeader().getLogfileOffset(), e);
            }
            CanalEntry.Header header = entry.getHeader();
            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                events.add(new CanalEventMessage(header.getSchemaName(),
                                                 header.getTableName(),
                                                 rowChange.getEventType().name(),
                                                 header.getExecuteTime(),
                                                 columnsToMap(rowData.getBeforeColumnsList()),
                                                 columnsToMap(rowData.getAfterColumnsList())));
            }
        }
        return events;
    }

    private Map<String, String> columnsToMap(List<CanalEntry.Column> columns) {
        Map<String, String> values = new LinkedHashMap<>();
        for (CanalEntry.Column column : columns) {
            values.put(column.getName(), column.getIsNull() ? null : column.getValue());
        }
        return values;
    }
}
