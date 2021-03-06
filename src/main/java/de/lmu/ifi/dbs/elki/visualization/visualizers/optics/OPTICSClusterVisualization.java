package de.lmu.ifi.dbs.elki.visualization.visualizers.optics;

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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;

import de.lmu.ifi.dbs.elki.data.Cluster;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.model.OPTICSModel;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.result.HierarchicalResult;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.result.ResultUtil;
import de.lmu.ifi.dbs.elki.utilities.datastructures.hierarchy.Hierarchy;
import de.lmu.ifi.dbs.elki.visualization.VisualizationTask;
import de.lmu.ifi.dbs.elki.visualization.colors.ColorLibrary;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClass;
import de.lmu.ifi.dbs.elki.visualization.projector.OPTICSProjector;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.AbstractVisFactory;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualization;

/**
 * Visualize the clusters and cluster hierarchy found by OPTICS on the OPTICS
 * Plot.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.stereotype factory
 * @apiviz.uses Instance oneway - - «create»
 */
public class OPTICSClusterVisualization extends AbstractVisFactory {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(OPTICSClusterVisualization.class);

  /**
   * A short name characterizing this Visualizer.
   */
  private static final String NAME = "OPTICS Cluster Ranges";

  /**
   * Constructor, adhering to
   * {@link de.lmu.ifi.dbs.elki.utilities.optionhandling.Parameterizable}
   */
  public OPTICSClusterVisualization() {
    super();
  }

  @Override
  public void processNewResult(HierarchicalResult baseResult, Result result) {
    Collection<OPTICSProjector<?>> ops = ResultUtil.filterResults(result, OPTICSProjector.class);
    for (OPTICSProjector<?> p : ops) {
      final Clustering<OPTICSModel> ocl = findOPTICSClustering(baseResult);
      if (ocl != null) {
        final VisualizationTask task = new VisualizationTask(NAME, ocl, null, this);
        task.level = VisualizationTask.LEVEL_DATA;
        baseResult.getHierarchy().add(p, task);
      }
    }
    // TODO: also run when a new clustering is added, instead of just new
    // projections?
  }

  @Override
  public Visualization makeVisualization(VisualizationTask task) {
    return new Instance<DoubleDistance>(task);
  }

  @Override
  public boolean allowThumbnails(VisualizationTask task) {
    // Don't use thumbnails
    return false;
  }

  /**
   * Find the first OPTICS clustering child of a result.
   * 
   * @param result Result to start searching at
   * @return OPTICS clustering
   */
  @SuppressWarnings("unchecked")
  protected static Clustering<OPTICSModel> findOPTICSClustering(Result result) {
    Collection<Clustering<?>> cs = ResultUtil.filterResults(result, Clustering.class);
    for (Clustering<?> clus : cs) {
      if (clus.getToplevelClusters().size() == 0) {
        continue;
      }
      try {
        Cluster<?> firstcluster = clus.getToplevelClusters().iterator().next();
        if (firstcluster.getModel() instanceof OPTICSModel) {
          return (Clustering<OPTICSModel>) clus;
        }
      } catch (Exception e) {
        // Empty clustering? Shouldn't happen.
        LOG.warning("Clustering with no cluster detected.", e);
      }
    }
    return null;
  }

  /**
   * Instance.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.uses Clustering oneway - - «visualizes»
   * 
   * @param <D> Distance type (actually unused)
   */
  public class Instance<D extends Distance<D>> extends AbstractOPTICSVisualization<D> {
    /**
     * CSS class for markers
     */
    protected static final String CSS_BRACKET = "opticsBracket";

    /**
     * Our clustering
     */
    Clustering<OPTICSModel> clus;

    /**
     * Constructor.
     * 
     * @param task Visualization task
     */
    public Instance(VisualizationTask task) {
      super(task);
      this.clus = task.getResult();
      context.addResultListener(this);
      incrementalRedraw();
    }

    @Override
    protected void redraw() {
      makeLayerElement();
      addCSSClasses();

      ColorLibrary colors = context.getStyleResult().getStyleLibrary().getColorSet(StyleLibrary.PLOT);
      HashMap<Cluster<?>, String> colormap = new HashMap<>();
      int cnum = 0;
      for (Cluster<?> c : clus.getAllClusters()) {
        colormap.put(c, colors.getColor(cnum));
        cnum++;
      }
      drawClusters(clus, clus.iterToplevelClusters(), 1, colormap);
    }

    /**
     * Recursively draw clusters
     * 
     * @param clusters Current set of clusters
     * @param depth Recursion depth
     * @param colormap Color mapping
     */
    private void drawClusters(Clustering<OPTICSModel> clustering, Hierarchy.Iter<Cluster<OPTICSModel>> clusters, int depth, Map<Cluster<?>, String> colormap) {
      final double scale = StyleLibrary.SCALE;

      for (; clusters.valid(); clusters.advance()) {
        Cluster<OPTICSModel> cluster = clusters.get();
        try {
          OPTICSModel model = cluster.getModel();
          final double x1 = plotwidth * ((model.getStartIndex() + .25) / this.optics.getResult().getClusterOrder().size());
          final double x2 = plotwidth * ((model.getEndIndex() + .75) / this.optics.getResult().getClusterOrder().size());
          final double y = plotheight + depth * scale * 0.01;
          Element e = svgp.svgLine(x1, y, x2, y);
          SVGUtil.addCSSClass(e, CSS_BRACKET);
          String color = colormap.get(cluster);
          if (color != null) {
            SVGUtil.setAtt(e, SVGConstants.SVG_STYLE_ATTRIBUTE, SVGConstants.CSS_STROKE_PROPERTY + ":" + color);
          }
          layer.appendChild(e);
        } catch (ClassCastException e) {
          LOG.warning("Expected OPTICSModel, got: " + cluster.getModel().getClass().getSimpleName());
        }
        // Descend
        final Hierarchy.Iter<Cluster<OPTICSModel>> children = clustering.getClusterHierarchy().iterChildren(cluster);
        if (children != null) {
          drawClusters(clustering, children, depth + 1, colormap);
        }
      }
    }

    /**
     * Adds the required CSS-Classes
     */
    private void addCSSClasses() {
      // Class for the markers
      if (!svgp.getCSSClassManager().contains(CSS_BRACKET)) {
        final CSSClass cls = new CSSClass(this, CSS_BRACKET);
        final StyleLibrary style = context.getStyleResult().getStyleLibrary();
        cls.setStatement(SVGConstants.CSS_STROKE_PROPERTY, style.getColor(StyleLibrary.PLOT));
        cls.setStatement(SVGConstants.CSS_STROKE_WIDTH_PROPERTY, style.getLineWidth(StyleLibrary.PLOT));
        svgp.addCSSClassOrLogError(cls);
      }
    }
  }
}
