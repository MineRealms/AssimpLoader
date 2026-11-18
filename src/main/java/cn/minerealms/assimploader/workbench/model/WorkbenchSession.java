package cn.minerealms.assimploader.workbench.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** In-memory edit transactions. A continuous drag contributes one history entry. */
public final class WorkbenchSession {
    private static final int HISTORY_LIMIT = 128;
    private final Deque<WorkbenchDocument> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private WorkbenchDocument draft, saved, transactionStart;
    private long revision;

    public WorkbenchSession(WorkbenchDocument document) { draft=saved=Objects.requireNonNull(document); }
    public WorkbenchDocument document() {return draft;}
    public long revision() {return revision;}
    public boolean dirty() {return !draft.equals(saved);}
    public boolean canUndo() {return !undo.isEmpty();}
    public boolean canRedo() {return !redo.isEmpty();}
    public void edit(UnaryOperator<WorkbenchDocument> edit) {set(edit.apply(draft));}
    public void set(WorkbenchDocument value) {
        Objects.requireNonNull(value);
        if(draft.equals(value))return;
        if(transactionStart==null){push(undo,draft);redo.clear();}
        draft=value;revision++;
    }
    public void beginTransaction() {if(transactionStart==null)transactionStart=draft;}
    public void commitTransaction() {
        if(transactionStart==null)return;
        if(!transactionStart.equals(draft)){push(undo,transactionStart);redo.clear();}
        transactionStart=null;
    }
    public void cancelTransaction() {
        if(transactionStart!=null){draft=transactionStart;transactionStart=null;revision++;}
    }
    public void undo() {commitTransaction();if(!undo.isEmpty()){push(redo,draft);draft=undo.removeLast();revision++;}}
    public void redo() {commitTransaction();if(!redo.isEmpty()){push(undo,draft);draft=redo.removeLast();revision++;}}
    public void revert() {transactionStart=null;undo.clear();redo.clear();draft=saved;revision++;}
    public void markSaved() {commitTransaction();saved=draft;}
    private static void push(Deque<WorkbenchDocument> history,WorkbenchDocument value) {
        history.addLast(value);while(history.size()>HISTORY_LIMIT)history.removeFirst();
    }
}
