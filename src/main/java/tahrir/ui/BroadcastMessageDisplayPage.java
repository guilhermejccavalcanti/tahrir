package tahrir.ui;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tahrir.TrUI;
import tahrir.io.net.broadcasts.broadcastMessages.BroadcastMessage;
import tahrir.io.net.broadcasts.broadcastMessages.ParsedBroadcastMessage;
import tahrir.vaadin.TestVaadinUI;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.SortedSet;

public class BroadcastMessageDisplayPage {
  private final JComponent content;

  private final MicroblogTableModel tableModel;

  private final EventBus eventBus;

  private final Predicate<BroadcastMessage> filter;

  private static final Logger logger = LoggerFactory.getLogger(BroadcastMessageDisplayPage.class.getName());

  public BroadcastMessageDisplayPage(final Predicate<BroadcastMessage> filter, final TrUI mainWindow) {
    this.filter = filter;
    eventBus = mainWindow.getNode().mbClasses.eventBus;
    tableModel = new MicroblogTableModel();
    JTable table = new JTable(tableModel);
    final BroadcastMessageRenderer renderer = new BroadcastMessageRenderer(mainWindow);
    table.setFillsViewportHeight(true);
    table.setGridColor(new Color(244, 242, 242));
    table.setDefaultRenderer(ParsedBroadcastMessage.class, renderer);
    table.setDefaultEditor(ParsedBroadcastMessage.class, renderer);
    final JScrollPane scrollPane = new JScrollPane();
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setViewportView(table);
    content = scrollPane;
    logger.debug("EventBus registered");
    eventBus.register(this);
    final SortedSet<BroadcastMessage> existingMicroblogs = mainWindow.getNode().mbClasses.mbsForViewing.getMicroblogSet();
    for (BroadcastMessage broadcastMessage : existingMicroblogs) {
      if (filter.apply(broadcastMessage)) {
        tableModel.addNewMicroblog(broadcastMessage);
      }
    }
  }

  @Subscribe public void modifyMicroblogsDisplay(BroadcastMessageModifiedEvent event) {
    if (event.type.equals(BroadcastMessageModifiedEvent.ModificationType.RECEIVED)) {
      if (filter.apply(event.broadcastMessage)) {
        tableModel.addNewMicroblog(event.broadcastMessage);
      }
    }
    if (event.type.equals(BroadcastMessageModifiedEvent.ModificationType.BOOSTED)) {
      tableModel.fireTableDataChanged();
    }
  }

  public JComponent getContent() {
    return content;
  }

  private class BroadcastMessagesComparator implements Comparator<BroadcastMessage> {
    @Override public int compare(BroadcastMessage bm1, BroadcastMessage bm2) {
      return Integer.toString(bm1.priority).compareTo(Integer.toString(bm2.priority));
    }
  }

  public MicroblogTableModel getTableModel() {
    return tableModel;
  }

  @SuppressWarnings(value = { "serial" }) public class MicroblogTableModel extends AbstractTableModel {
    private final ArrayList<BroadcastMessage> broadcastMessages;

    public MicroblogTableModel() {
      broadcastMessages = Lists.newArrayList();
    }

    @Override public int getColumnCount() {
      return 1;
    }

    @Override public int getRowCount() {
      return broadcastMessages.size();
    }

    @Override public Object getValueAt(final int row, final int col) {
      return broadcastMessages.get(row);
    }

    public void addNewMicroblog(final BroadcastMessage bm) {
      broadcastMessages.add(0, bm);
      Collections.sort(broadcastMessages, new BroadcastMessagesComparator());
      this.fireTableRowsInserted(0, tableModel.getRowCount());
    }

    public void removeMicroblog(final ParsedBroadcastMessage mb) {
      final int mbIndex = broadcastMessages.indexOf(mb);
      broadcastMessages.remove(mbIndex);
    }

    @Override public Class<?> getColumnClass(final int columnIndex) {
      return ParsedBroadcastMessage.class;
    }

    @Override public String getColumnName(final int columnIndex) {
      return null;
    }

    @Override public boolean isCellEditable(final int rowIndex, final int columnIndex) {
      return true;
    }

    public ArrayList<BroadcastMessage> getBroadcastMessages() {
      return broadcastMessages;
    }
  }
}