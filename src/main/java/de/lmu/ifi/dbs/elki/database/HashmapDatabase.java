package de.lmu.ifi.dbs.elki.database;

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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;

import de.lmu.ifi.dbs.elki.data.type.SimpleTypeInformation;
import de.lmu.ifi.dbs.elki.database.ids.ArrayModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDFactory;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.HashSetModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.relation.DBIDView;
import de.lmu.ifi.dbs.elki.database.relation.MaterializedRelation;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.datasource.DatabaseConnection;
import de.lmu.ifi.dbs.elki.datasource.FileBasedDatabaseConnection;
import de.lmu.ifi.dbs.elki.datasource.bundle.MultipleObjectsBundle;
import de.lmu.ifi.dbs.elki.datasource.bundle.ObjectBundle;
import de.lmu.ifi.dbs.elki.datasource.bundle.SingleObjectBundle;
import de.lmu.ifi.dbs.elki.index.DynamicIndex;
import de.lmu.ifi.dbs.elki.index.Index;
import de.lmu.ifi.dbs.elki.index.IndexFactory;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.Parameterizable;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectListParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;

/**
 * Provides a mapping for associations based on a Hashtable and functions to get
 * the next usable ID for insertion, making IDs reusable after deletion of the
 * entry.
 * 
 * @author Arthur Zimek
 * @author Erich Schubert
 * 
 * @apiviz.landmark
 * @apiviz.composedOf HashSetModifiableDBIDs
 */
@Description("Database using an in-memory hashtable and at least providing linear scans.")
public class HashmapDatabase extends AbstractDatabase implements UpdatableDatabase, Parameterizable {
  /**
   * Our logger
   */
  private static final Logging LOG = Logging.getLogger(HashmapDatabase.class);

  /**
   * IDs of this database
   */
  private HashSetModifiableDBIDs ids;

  /**
   * The DBID representation we use
   */
  private final DBIDView idrep;

  /**
   * The data source we get the initial data from.
   */
  protected DatabaseConnection databaseConnection;

  /**
   * Constructor.
   * 
   * @param databaseConnection Database connection to get the initial data from.
   * @param indexFactories Indexes to add
   */
  public HashmapDatabase(DatabaseConnection databaseConnection, Collection<IndexFactory<?, ?>> indexFactories) {
    super();
    this.databaseConnection = databaseConnection;
    this.ids = DBIDUtil.newHashSet();
    this.idrep = new DBIDView(this, this.ids);
    this.relations.add(idrep);
    this.addChildResult(idrep);

    // Add indexes.
    if (indexFactories != null) {
      this.indexFactories.addAll(indexFactories);
    }
  }

  /**
   * Constructor with no indexes.
   */
  public HashmapDatabase() {
    this(null, null);
  }

  /**
   * Initialize the database by getting the initial data from the database
   * connection.
   */
  @Override
  public void initialize() {
    if (databaseConnection != null) {
      this.insert(databaseConnection.loadData());
      // Run at most once.
      databaseConnection = null;
    }
  }

  @Override
  public DBIDs insert(ObjectBundle objpackages) {
    if (objpackages.dataLength() == 0) {
      return DBIDUtil.EMPTYDBIDS;
    }
    // insert into db
    ArrayModifiableDBIDs newids = DBIDUtil.newArray(objpackages.dataLength());
    Relation<?>[] targets = alignColumns(objpackages);

    int idrepnr = -1;
    for (int i = 0; i < targets.length; i++) {
      if (targets[i] == idrep) {
        idrepnr = i;
        break;
      }
    }

    for (int j = 0; j < objpackages.dataLength(); j++) {
      // insert object
      final DBID newid;
      if (idrepnr < 0) {
        newid = DBIDUtil.generateSingleDBID();
      } else {
        newid = (DBID) objpackages.data(j, idrepnr);
      }
      if (ids.contains(newid)) {
        throw new AbortException("Duplicate DBID conflict.");
      }
      ids.add(newid);
      for (int i = 0; i < targets.length; i++) {
        // DBIDs were handled above.
        if (i == idrepnr) {
          continue;
        }
        @SuppressWarnings("unchecked")
        final Relation<Object> relation = (Relation<Object>) targets[i];
        relation.set(newid, objpackages.data(j, i));
      }
      newids.add(newid);
    }

    // Notify indexes of insertions
    for (Index index : indexes) {
      if (index instanceof DynamicIndex) {
        ((DynamicIndex) index).insertAll(newids);
      } else {
        LOG.warning("Non-dynamic indexes have been added to the database. Updates are not possible!");
      }
    }

    // fire insertion event
    eventManager.fireObjectsInserted(newids);
    return newids;
  }

  /**
   * Find a mapping from package columns to database columns, eventually adding
   * new database columns when needed.
   * 
   * @param pack Package to process
   * @return Column mapping
   */
  protected Relation<?>[] alignColumns(ObjectBundle pack) {
    // align representations.
    Relation<?>[] targets = new Relation<?>[pack.metaLength()];
    {
      BitSet used = new BitSet(relations.size());
      for (int i = 0; i < targets.length; i++) {
        SimpleTypeInformation<?> meta = pack.meta(i);
        // TODO: aggressively try to match exact metas first?
        // Try to match unused representations only
        for (int j = used.nextClearBit(0); j >= 0 && j < relations.size(); j = used.nextClearBit(j + 1)) {
          Relation<?> relation = relations.get(j);
          if (relation.getDataTypeInformation().isAssignableFromType(meta)) {
            targets[i] = relation;
            used.set(j);
            break;
          }
        }
        if (targets[i] == null) {
          targets[i] = addNewRelation(meta);
          used.set(relations.size() - 1);
        }
      }
    }
    return targets;
  }

  /**
   * Add a new representation for the given meta.
   * 
   * @param meta meta data
   * @return new representation
   */
  private Relation<?> addNewRelation(SimpleTypeInformation<?> meta) {
    @SuppressWarnings("unchecked")
    SimpleTypeInformation<Object> ometa = (SimpleTypeInformation<Object>) meta;
    Relation<?> relation = new MaterializedRelation<>(this, ometa, ids);
    relations.add(relation);
    getHierarchy().add(this, relation);
    // Try to add indexes where appropriate
    for (IndexFactory<?, ?> factory : indexFactories) {
      if (factory.getInputTypeRestriction().isAssignableFromType(meta)) {
        @SuppressWarnings("unchecked")
        final IndexFactory<Object, ?> ofact = (IndexFactory<Object, ?>) factory;
        @SuppressWarnings("unchecked")
        final Relation<Object> orep = (Relation<Object>) relation;
        Index index = ofact.instantiate(orep);
        addIndex(index);
        index.initialize();
      }
    }
    return relation;
  }

  /**
   * Removes the objects from the database (by calling
   * {@link #doDelete(DBIDRef)} for each object) and indexes and fires a
   * deletion event.
   * 
   * {@inheritDoc}
   */
  @Override
  public MultipleObjectsBundle delete(DBIDs ids) {
    // Prepare bundle to return
    MultipleObjectsBundle bundle = new MultipleObjectsBundle();
    for (Relation<?> relation : relations) {
      ArrayList<Object> data = new ArrayList<>(ids.size());
      for (DBIDIter iter = ids.iter(); iter.valid(); iter.advance()) {
        data.add(relation.get(iter));
      }
      bundle.appendColumn(relation.getDataTypeInformation(), data);
    }
    // remove from db
    for (DBIDIter iter = ids.iter(); iter.valid(); iter.advance()) {
      doDelete(iter);
    }
    // Remove from indexes
    for (Index index : indexes) {
      if (index instanceof DynamicIndex) {
        ((DynamicIndex) index).deleteAll(ids);
      } else {
        LOG.warning("Non-dynamic indexes have been added to the database. Updates are not possible!");
      }

    }
    // fire deletion event
    eventManager.fireObjectsRemoved(ids);

    return bundle;
  }

  /**
   * Removes the object from the database (by calling {@link #doDelete(DBIDRef)}
   * ) and indexes and fires a deletion event.
   * 
   * {@inheritDoc}
   */
  @Override
  public SingleObjectBundle delete(DBIDRef id) {
    // Prepare bundle to return
    SingleObjectBundle bundle = new SingleObjectBundle();
    for (Relation<?> relation : relations) {
      bundle.append(relation.getDataTypeInformation(), relation.get(id));
    }
    doDelete(id);
    // Remove from indexes
    for (Index index : indexes) {
      if (index instanceof DynamicIndex) {
        ((DynamicIndex) index).delete(id);
      } else {
        LOG.warning("Non-dynamic indexes have been added to the database. Updates are not possible!");
      }
    }
    // fire deletion event
    eventManager.fireObjectRemoved(id);

    return bundle;
  }

  /**
   * Removes the object with the specified id from this database.
   * 
   * @param id id the id of the object to be removed
   */
  private void doDelete(DBIDRef id) {
    // Remove id
    ids.remove(id);
    // Remove from all representations.
    for (Relation<?> relation : relations) {
      // ID has already been removed, and this would loop...
      if (relation != idrep) {
        relation.delete(id);
      }
    }
    DBIDFactory.FACTORY.deallocateSingleDBID(id);
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractDatabase.Parameterizer {
    /**
     * Holds the database connection to get the initial data from.
     */
    protected DatabaseConnection databaseConnection = null;

    /**
     * Indexes to add.
     */
    private Collection<IndexFactory<?, ?>> indexFactories;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      // Get database connection.
      final ObjectParameter<DatabaseConnection> dbcP = new ObjectParameter<>(DATABASE_CONNECTION_ID, DatabaseConnection.class, FileBasedDatabaseConnection.class);
      if (config.grab(dbcP)) {
        databaseConnection = dbcP.instantiateClass(config);
      }
      // Get indexes.
      final ObjectListParameter<IndexFactory<?, ?>> indexFactoryP = new ObjectListParameter<>(INDEX_ID, IndexFactory.class, true);
      if (config.grab(indexFactoryP)) {
        indexFactories = indexFactoryP.instantiateClasses(config);
      }
    }

    @Override
    protected HashmapDatabase makeInstance() {
      return new HashmapDatabase(databaseConnection, indexFactories);
    }
  }
}
