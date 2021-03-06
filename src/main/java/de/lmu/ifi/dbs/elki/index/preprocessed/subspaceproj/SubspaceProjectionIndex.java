package de.lmu.ifi.dbs.elki.index.preprocessed.subspaceproj;

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

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.index.preprocessed.LocalProjectionIndex;
import de.lmu.ifi.dbs.elki.math.linearalgebra.ProjectionResult;

/**
 * Interface for an index providing local subspaces.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.landmark
 * 
 * @param <NV> Vector type
 */
public interface SubspaceProjectionIndex<NV extends NumberVector<?>, P extends ProjectionResult> extends LocalProjectionIndex<NV, P> {
  /**
   * Get the precomputed local subspace for a particular object ID.
   * 
   * @param objid Object ID
   * @return Matrix
   */
  @Override
  public P getLocalProjection(DBIDRef objid);

  /**
   * Factory interface
   * 
   * @author Erich Schubert
   * 
   * @apiviz.landmark
   * @apiviz.stereotype factory
   * @apiviz.uses SubspaceProjectionIndex oneway - - «create»
   * 
   * @param <NV> Vector type
   * @param <I> Index type produced
   */
  public static interface Factory<NV extends NumberVector<?>, I extends SubspaceProjectionIndex<NV, ?>> extends LocalProjectionIndex.Factory<NV, I> {
    /**
     * Instantiate the index for a given database.
     * 
     * @param relation Relation
     * 
     * @return Index
     */
    @Override
    public I instantiate(Relation<NV> relation);
  }
}