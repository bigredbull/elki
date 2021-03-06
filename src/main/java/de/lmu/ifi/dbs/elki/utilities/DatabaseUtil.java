package de.lmu.ifi.dbs.elki.utilities;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2013
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import de.lmu.ifi.dbs.elki.data.ClassLabel;
import de.lmu.ifi.dbs.elki.data.FeatureVector;
import de.lmu.ifi.dbs.elki.data.LabelList;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.type.NoSupportedDataTypeException;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.ArrayDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.ArrayModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.relation.ConvertToStringView;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.database.relation.RelationUtil;
import de.lmu.ifi.dbs.elki.utilities.pairs.Pair;

/**
 * Class with Database-related utility functions such as centroid computation,
 * covariances etc.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.landmark
 * 
 * @apiviz.has RelationObjectIterator
 * @apiviz.has CollectionFromRelation
 */
public final class DatabaseUtil {
  /**
   * Fake constructor: Do not instantiate!
   */
  private DatabaseUtil() {
    // Do not instantiate!
  }
  
  /**
   * Get the dimensionality of a relation.
   * 
   * @param relation Relation
   * @return Dimensionality
   * 
   * @deprecated Use {@link RelationUtil#dimensionality(Relation)} instead!
   */
  @Deprecated
  public static <V extends FeatureVector<?>> int dimensionality(Relation<V> relation) {
    return RelationUtil.dimensionality(relation);
  }

  /**
   * Determines the variances in each dimension of the specified objects stored
   * in the given database.
   * 
   * @param database the database storing the objects
   * @param ids the ids of the objects
   * @param centroid the centroid or reference vector of the ids
   * @return the variances in each dimension of the specified objects
   */
  public static double[] variances(Relation<? extends NumberVector<?>> database, NumberVector<?> centroid, DBIDs ids) {
    final int size = ids.size();
    double[] variances = new double[centroid.getDimensionality()];

    for (DBIDIter iter = ids.iter(); iter.valid(); iter.advance()) {
      NumberVector<?> o = database.get(iter);
      for (int d = 0; d < centroid.getDimensionality(); d++) {
        final double diff = o.doubleValue(d) - centroid.doubleValue(d);
        variances[d ] += diff * diff / size;
      }
    }
    return variances;
  }

  /**
   * Determines the minimum and maximum values in each dimension of all objects
   * stored in the given database.
   * 
   * @param <NV> vector type
   * @param relation the database storing the objects
   * @return Minimum and Maximum vector for the hyperrectangle
   */
  public static <NV extends NumberVector<?>> Pair<NV, NV> computeMinMax(Relation<NV> relation) {
    int dim = RelationUtil.dimensionality(relation);
    double[] mins = new double[dim];
    double[] maxs = new double[dim];
    for (int i = 0; i < dim; i++) {
      mins[i] = Double.MAX_VALUE;
      maxs[i] = -Double.MAX_VALUE;
    }
    for (DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      final NV o = relation.get(iditer);
      for (int d = 0; d < dim; d++) {
        final double v = o.doubleValue(d);
        mins[d] = Math.min(mins[d], v);
        maxs[d] = Math.max(maxs[d], v);
      }
    }
    NumberVector.Factory<NV, ?> factory = RelationUtil.getNumberVectorFactory(relation);
    NV min = factory.newNumberVector(mins);
    NV max = factory.newNumberVector(maxs);
    return new Pair<>(min, max);
  }

  /**
   * Returns the median of a data set in the given dimension by using a sampling
   * method.
   * 
   * @param relation Relation to process
   * @param ids DBIDs to process
   * @param dimension Dimensionality
   * @param numberOfSamples Number of samples to draw
   * @return Median value
   */
  public static <V extends NumberVector<?>> double quickMedian(Relation<V> relation, ArrayDBIDs ids, int dimension, int numberOfSamples) {
    final int everyNthItem = (int) Math.max(1, Math.floor(ids.size() / (double) numberOfSamples));
    final double[] vals = new double[numberOfSamples];
    for (int i = 0; i < numberOfSamples; i++) {
      final DBID id = ids.get(i * everyNthItem);
      vals[i] = relation.get(id).doubleValue(dimension);
    }
    Arrays.sort(vals);
    if (vals.length % 2 == 1) {
      return vals[((vals.length + 1) >> 1) - 1];
    } else {
      final double v1 = vals[vals.length >> 1];
      final double v2 = vals[(vals.length >> 1) - 1];
      return (v1 + v2) / 2.0;
    }
  }

  /**
   * Returns the median of a data set in the given dimension.
   * 
   * @param relation Relation to process
   * @param ids DBIDs to process
   * @param dimension Dimensionality
   * @return Median value
   */
  public static <V extends NumberVector<?>> double exactMedian(Relation<V> relation, DBIDs ids, int dimension) {
    final double[] vals = new double[ids.size()];
    int i = 0;
    for (DBIDIter iter = ids.iter(); iter.valid(); iter.advance()) {
      vals[i] = relation.get(iter).doubleValue(dimension);
      i++;
    }
    Arrays.sort(vals);
    if (vals.length % 2 == 1) {
      return vals[((vals.length + 1) >> 1) - 1];
    } else {
      final double v1 = vals[vals.length >> 1];
      final double v2 = vals[(vals.length >> 1) - 1];
      return (v1 + v2) / 2.0;
    }
  }

  /**
   * Guess a potentially label-like representation, preferring class labels.
   * 
   * @param database
   * @return string representation
   */
  public static Relation<String> guessLabelRepresentation(Database database) throws NoSupportedDataTypeException {
    try {
      Relation<? extends ClassLabel> classrep = database.getRelation(TypeUtil.CLASSLABEL);
      if (classrep != null) {
        return new ConvertToStringView(classrep);
      }
    } catch (NoSupportedDataTypeException e) {
      // retry.
    }
    try {
      Relation<? extends LabelList> labelsrep = database.getRelation(TypeUtil.LABELLIST);
      if (labelsrep != null) {
        return new ConvertToStringView(labelsrep);
      }
    } catch (NoSupportedDataTypeException e) {
      // retry.
    }
    try {
      Relation<String> stringrep = database.getRelation(TypeUtil.STRING);
      if (stringrep != null) {
        return stringrep;
      }
    } catch (NoSupportedDataTypeException e) {
      // retry.
    }
    throw new NoSupportedDataTypeException("No label-like representation was found.");
  }

  /**
   * Guess a potentially object label-like representation.
   * 
   * @param database
   * @return string representation
   */
  public static Relation<String> guessObjectLabelRepresentation(Database database) throws NoSupportedDataTypeException {
    try {
      Relation<? extends LabelList> labelsrep = database.getRelation(TypeUtil.LABELLIST);
      if (labelsrep != null) {
        return new ConvertToStringView(labelsrep);
      }
    } catch (NoSupportedDataTypeException e) {
      // retry.
    }
    try {
      Relation<String> stringrep = database.getRelation(TypeUtil.STRING);
      if (stringrep != null) {
        return stringrep;
      }
    } catch (NoSupportedDataTypeException e) {
      // retry.
    }
    try {
      Relation<? extends ClassLabel> classrep = database.getRelation(TypeUtil.CLASSLABEL);
      if (classrep != null) {
        return new ConvertToStringView(classrep);
      }
    } catch (NoSupportedDataTypeException e) {
      // retry.
    }
    throw new NoSupportedDataTypeException("No label-like representation was found.");
  }

  /**
   * Retrieves all class labels within the database.
   * 
   * @param database the database to be scanned for class labels
   * @return a set comprising all class labels that are currently set in the
   *         database
   */
  public static SortedSet<ClassLabel> getClassLabels(Relation<? extends ClassLabel> database) {
    SortedSet<ClassLabel> labels = new TreeSet<>();
    for (DBIDIter it = database.iterDBIDs(); it.valid(); it.advance()) {
      labels.add(database.get(it));
    }
    return labels;
  }

  /**
   * Retrieves all class labels within the database.
   * 
   * @param database the database to be scanned for class labels
   * @return a set comprising all class labels that are currently set in the
   *         database
   */
  public static SortedSet<ClassLabel> getClassLabels(Database database) {
    final Relation<ClassLabel> relation = database.getRelation(TypeUtil.CLASSLABEL);
    return getClassLabels(relation);
  }

  /**
   * Do a cheap guess at the databases object class.
   * 
   * @param <O> Restriction type
   * @param database Database
   * @return Class of first object in the Database.
   */
  @SuppressWarnings("unchecked")
  public static <O> Class<? extends O> guessObjectClass(Relation<O> database) {
    return (Class<? extends O>) database.get(database.iterDBIDs()).getClass();
  }

  /**
   * Do a full inspection of the database to find the base object class.
   * 
   * Note: this can be an abstract class or interface!
   * 
   * TODO: Implement a full search for shared superclasses. But since currently
   * the databases will always use only once class, this is not yet implemented.
   * 
   * @param <O> Restriction type
   * @param database Database
   * @return Superclass of all objects in the database
   */
  public static <O> Class<?> getBaseObjectClassExpensive(Relation<O> database) {
    List<Class<?>> candidates = new ArrayList<>();
    DBIDIter iditer = database.iterDBIDs();
    // empty database?!
    if (!iditer.valid()) {
      return null;
    }
    // put first class into result set.
    candidates.add(database.get(iditer).getClass());
    iditer.advance();
    // other objects
    for (; iditer.valid(); iditer.advance()) {
      Class<?> newcls = database.get(iditer).getClass();
      // validate all candidates
      Iterator<Class<?>> ci = candidates.iterator();
      while (ci.hasNext()) {
        Class<?> cand = ci.next();
        if (cand.isAssignableFrom(newcls)) {
          continue;
        }
        // TODO: resolve conflicts by finding all superclasses!
        // Does this code here work?
        for (Class<?> interf : cand.getInterfaces()) {
          candidates.add(interf);
        }
        candidates.add(cand.getSuperclass());
        ci.remove();
      }
    }
    // if we have any candidates left ...
    if (candidates.size() > 0) {
      // remove subclasses
      Iterator<Class<?>> ci = candidates.iterator();
      while (ci.hasNext()) {
        Class<?> cand = ci.next();
        for (Class<?> oc : candidates) {
          if (!oc.equals(cand) && cand.isAssignableFrom(oc)) {
            ci.remove();
            break;
          }
        }
      }
      assert (candidates.size() > 0);
      try {
        return candidates.get(0);
      } catch (ClassCastException e) {
        // ignore, and retry with next
      }
    }
    // no resulting class.
    return null;
  }

  /**
   * Find object by matching their labels.
   * 
   * @param database Database to search in
   * @param name_pattern Name to match against class or object label
   * @return found cluster or it throws an exception.
   */
  public static ArrayModifiableDBIDs getObjectsByLabelMatch(Database database, Pattern name_pattern) {
    Relation<String> relation = guessLabelRepresentation(database);
    if (name_pattern == null) {
      return DBIDUtil.newArray();
    }
    ArrayModifiableDBIDs ret = DBIDUtil.newArray();
    for (DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      if (name_pattern.matcher(relation.get(iditer)).find()) {
        ret.add(iditer);
      }
    }
    return ret;
  }

  /**
   * An ugly vector type cast unavoidable in some situations due to Generics.
   * 
   * @param <V> Base vector type
   * @param <T> Derived vector type (is actually V, too)
   * @param database Database
   * @return Database
   */
  @SuppressWarnings("unchecked")
  public static <V extends NumberVector<?>, T extends NumberVector<?>> Relation<V> relationUglyVectorCast(Relation<T> database) {
    return (Relation<V>) database;
  }

  /**
   * Iterator class that retrieves the given objects from the database.
   * 
   * @author Erich Schubert
   */
  public static class RelationObjectIterator<O> implements Iterator<O> {
    /**
     * The real iterator.
     */
    final DBIDIter iter;

    /**
     * The database we use.
     */
    final Relation<? extends O> database;

    /**
     * Full Constructor.
     * 
     * @param iter Original iterator.
     * @param database Database
     */
    public RelationObjectIterator(DBIDIter iter, Relation<? extends O> database) {
      super();
      this.iter = iter;
      this.database = database;
    }

    /**
     * Simplified constructor.
     * 
     * @param database Database
     */
    public RelationObjectIterator(Relation<? extends O> database) {
      super();
      this.database = database;
      this.iter = database.iterDBIDs();
    }

    @Override
    public boolean hasNext() {
      return iter.valid();
    }

    @Override
    public O next() {
      O ret = database.get(iter);
      iter.advance();
      return ret;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Collection view on a database that retrieves the objects when needed.
   * 
   * @author Erich Schubert
   */
  public static class CollectionFromRelation<O> extends AbstractCollection<O> implements Collection<O> {
    /**
     * The database we query.
     */
    Relation<? extends O> db;

    /**
     * Constructor.
     * 
     * @param db Database
     */
    public CollectionFromRelation(Relation<? extends O> db) {
      super();
      this.db = db;
    }

    @Override
    public Iterator<O> iterator() {
      return new DatabaseUtil.RelationObjectIterator<>(db);
    }

    @Override
    public int size() {
      return db.size();
    }
  }
}
