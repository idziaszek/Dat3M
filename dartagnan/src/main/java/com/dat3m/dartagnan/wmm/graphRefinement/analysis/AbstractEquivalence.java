package com.dat3m.dartagnan.wmm.graphRefinement.analysis;

import java.util.*;

public abstract class AbstractEquivalence<T> implements Equivalence<T> {

    protected Map<T, EqClass> classMap;
    protected Set<EqClass> classes;

    @Override
    public EquivalenceClass<T> getEquivalenceClass(T x) {
        return classMap.get(x);
    }

    @Override
    public Set<? extends EquivalenceClass<T>> getAllEquivalenceClasses() {
        return classes;
    }

    public AbstractEquivalence() {
        classMap = new HashMap<>();
        classes = new HashSet<>();
    }

    protected AbstractEquivalence(int estimatedDataSize, int estimatedNumOfClasses) {
        // We make classMap larger by factor 4/3 to avoid resizing due to HashMap's LoadFactor
        classMap = new HashMap<>((4 * estimatedDataSize) / 3);
        classes = new HashSet<>(estimatedNumOfClasses);
    }

    protected boolean removeEmptyClasses() {
        return classes.removeIf(Set::isEmpty);
    }

    protected boolean removeClass(EqClass c) {
        if (classes.remove(c)) {
            c.forEach(classMap::remove);
            return true;
        }
        return false;
    }

    protected void mergeClasses(EqClass first, EqClass second) {
        first.internalSet.addAll(second);
        second.forEach(x -> classMap.put(x, first));
        classes.remove(second);
    }



    protected class EqClass extends AbstractSet<T> implements EquivalenceClass<T> {
        public T representative;
        public Set<T> internalSet;

        public EqClass() {
            this(-1);
        }

        public EqClass(int initialCapacity) {
            internalSet = initialCapacity < 1 ? new HashSet<>() : new HashSet<>(initialCapacity);
            classes.add(this);
        }

        public boolean addInternal(T x) {
            if (internalSet.add(x)) {
                EqClass oldClass = classMap.put(x, this);
                if (oldClass != null) {
                    oldClass.internalSet.remove(x);
                    if (x.equals(oldClass.representative)) {
                        oldClass.representative = oldClass.isEmpty() ? null : oldClass.stream().findAny().get();
                    }
                }
                return true;
            }
            return false;
        }

        public boolean addAllInternal(Collection<T> col) {
            boolean changed = false;
            for (T x : col) {
                changed |= addInternal(x);
            }
            return changed;
        }

        public void updateClassMap() {
            for (T x : internalSet) {
                classMap.put(x, this);
            }
        }


        //====================== Object =============================

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        //====================== Equivalence Class =============================

        @Override
        public Equivalence<T> getEquivalence() {
            return AbstractEquivalence.this;
        }

        @Override
        public void setRepresentative(T rep) {
            if (internalSet.contains(rep)) {
                representative = rep;
            }
        }

        @Override
        public T getRepresentative() {
            return representative;
        }


        //====================== Set =============================


        @Override
        public boolean isEmpty() {
            return internalSet.isEmpty();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return internalSet.containsAll(c);
        }

        @Override
        public String toString() {
            return internalSet.toString();
        }

        @Override
        public boolean contains(Object o) {
            return internalSet.contains(o);
        }

        @Override
        public Object[] toArray() {
            return internalSet.toArray();
        }

        @Override
        public <T1> T1[] toArray(T1[] a) {
            return internalSet.toArray(a);
        }

        @Override
        public Iterator<T> iterator() {
            return internalSet.iterator();
        }

        @Override
        public int size() {
            return internalSet.size();
        }
    }
}
