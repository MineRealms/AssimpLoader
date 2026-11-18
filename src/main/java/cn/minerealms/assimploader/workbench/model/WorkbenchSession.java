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
    public WorkbenchDocument savedDocument() {return saved;}
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
    public void markSaved() {commitTransaction();markSaved(draft);}
    /** A completed asynchronous write saves its snapshot, not any edits made while it ran. */
    public void markSaved(WorkbenchDocument persisted) {saved=Objects.requireNonNull(persisted);}
    /** Rebind copied asset paths without discarding newer edits, drag state or either history. */
    public void rebase(UnaryOperator<WorkbenchDocument> mapping) {
        Objects.requireNonNull(mapping);
        var nextDraft=Objects.requireNonNull(mapping.apply(draft));
        var nextSaved=Objects.requireNonNull(mapping.apply(saved));
        var nextStart=transactionStart==null?null:Objects.requireNonNull(mapping.apply(transactionStart));
        var nextUndo=remap(undo,mapping);var nextRedo=remap(redo,mapping);
        draft=nextDraft;saved=nextSaved;transactionStart=nextStart;
        undo.clear();undo.addAll(nextUndo);redo.clear();redo.addAll(nextRedo);revision++;
    }
    private static Deque<WorkbenchDocument> remap(Deque<WorkbenchDocument> history,UnaryOperator<WorkbenchDocument> mapping) {
        var result=new ArrayDeque<WorkbenchDocument>();
        for(var entry:history)result.addLast(Objects.requireNonNull(mapping.apply(entry)));
        return result;
    }
    private static void push(Deque<WorkbenchDocument> history,WorkbenchDocument value) {
        history.addLast(value);while(history.size()>HISTORY_LIMIT)history.removeFirst();
    }
}
