package cn.minerealms.assimploader.util;

import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;

/** Public declaring methods keep immutable snapshots callable through Java 17 script reflection. */
public final class ReadOnlyList<E> extends AbstractList<E> implements RandomAccess {
    private final List<E> values;

    private ReadOnlyList(Collection<? extends E> values) {this.values=List.copyOf(values);}
    public static <E> ReadOnlyList<E> copyOf(Collection<? extends E> values){return new ReadOnlyList<>(values);}
    @Override public E get(int index){return values.get(index);}
    @Override public int size(){return values.size();}
    @Override public ReadOnlyList<E> subList(int from,int to){return copyOf(values.subList(from,to));}
    @Override public Cursor<E> iterator(){return listIterator(0);}
    @Override public Cursor<E> listIterator(){return listIterator(0);}
    @Override public Cursor<E> listIterator(int index){return new Cursor<>(values.listIterator(index));}

    public static final class Cursor<E> implements ListIterator<E> {
        private final ListIterator<E> iterator;
        private Cursor(ListIterator<E> iterator){this.iterator=iterator;}
        @Override public boolean hasNext(){return iterator.hasNext();}
        @Override public E next(){return iterator.next();}
        @Override public boolean hasPrevious(){return iterator.hasPrevious();}
        @Override public E previous(){return iterator.previous();}
        @Override public int nextIndex(){return iterator.nextIndex();}
        @Override public int previousIndex(){return iterator.previousIndex();}
        @Override public void remove(){throw new UnsupportedOperationException("Read-only snapshot");}
        @Override public void set(E value){throw new UnsupportedOperationException("Read-only snapshot");}
        @Override public void add(E value){throw new UnsupportedOperationException("Read-only snapshot");}
    }
}
