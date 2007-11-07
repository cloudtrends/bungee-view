/*
 * 
 * Created on Mar 4, 2005
 * 
 * Bungee View lets you search, browse, and data-mine an image
 * collection. Copyright (C) 2006 Mark Derthick
 * 
 * This program is free software; you can redistribute it and/or modify it undercents
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version. See gpl.html.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 * 
 * You may also contact the author at mad@cs.cmu.edu, or at Mark Derthick
 * Carnegie-Mellon University Human-Computer Interaction Institute Pittsburgh,
 * PA 15213
 *  
 */

package edu.cmu.cs.bungee.client.viz;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.Color;
import java.awt.Component;
import java.awt.Paint;
import java.lang.Math;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.swing.SwingUtilities;

import edu.cmu.cs.bungee.client.query.ItemPredicate;
import edu.cmu.cs.bungee.client.query.Markup;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.Query;
import edu.cmu.cs.bungee.client.viz.Summary.RankComponentHeights;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.Arrow;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPPath;
import edu.cmu.cs.bungee.piccoloUtils.gui.MyInputEventHandler;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PBasicInputEventHandler;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.nodes.PPath;
import edu.umd.cs.piccolo.util.PBounds;
import edu.umd.cs.piccolo.util.PDimension;

final class PerspectiveViz extends LazyPNode implements PickFacetTextNotifier {

	Summary summary;

	Rank rank;

	PerspectiveViz parentPV;

	Perspective p;

	private SqueezablePNode labels;

	private LazyPNode parentRect;

	SqueezablePNode front;

	LazyPPath lightBeam = null;

	TextNfacets rankLabel;

	APText[] percentLabels;

	private LazyPNode percentLabelHotZone;

	private LazyPNode hotLine;

	// private double percentLabelW;

	final static double PERCENT_LABEL_SCALE = 0.75;

	static final Color pvBG = new Color(0x333344);

	/**
	 * Our logical width, of which leftEdge - rightEdge is visible.
	 */
	private int logicalWidth = 0;

	/**
	 * offset into logicalWidth of the leftmost visible pixel. Rightmost visible
	 * pixel is leftEdge + getWidth()
	 */
	private double leftEdge = 0;

	// private double rightEdge = 0;

	// /**
	// * The x midpoint of each facet (indexed by facet.index), whether or not
	// it
	// * is displayed. Length = number of facets.
	// */
	// private int[] x_mid_coords;

	private int labelHprojectionW;

	/**
	 * The facet of the bar at a given x coordinate. Only the endpoints are
	 * recorded. Noone should ask for coordinates in between the endpoints.
	 */
	private Perspective[] barXs;

	/**
	 * The facet of the label at a given x coordinate. All points are recorded.
	 */
	private Perspective[] labelXs;

	private Hashtable barTable = new Hashtable();

	/**
	 * Remember previous layout parameters, and don't re-layout if they are the
	 * same.
	 */
	private RankComponentHeights prevComponentHeights;

	double prevH;

	MedianArrow medianArrow;

	PerspectiveViz(Perspective _p, Summary _summary) {
		p = _p;
		summary = _summary;
		parentPV = summary.findPerspective(p.getParent());
		setPickable(false);

		// Bungee art = art();

		// Util.print("PerspectiveViz " + p.getName() + " " + p.nValues());

		front = new SqueezablePNode();
		front.setPaint(Color.black);
		front.setStroke(LazyPPath.getStrokeInstance(0));
		front.setHeight(1);
		// front.clip = new PBounds(-1000, 0.5, 2000, 0.5);
		addChild(front);
		// front isn't visible directly, so bounds don't matter.
		// front.setBounds(0, 0, 1, 1);
		front.addInputEventListener(Bungee.facetClickHandler);
		parentRect = new LazyPNode();
		parentRect.setPaint(pvBG);
		parentRect.setBounds(0, 0.5, 1, 0.5);

		labels = new SqueezablePNode();
		labels.setVisible(false);
		addChild(labels); // add labels after front, so labels get
		// picked in
		// favor of rankLabel
		labels.addInputEventListener(Bungee.facetClickHandler);

		rankLabel = new TextNfacets(art(), Bungee.summaryFG, true);
		rankLabel.setPickable(false);
		// rankLabel.unpickableAction = -2;
		rankLabel.setWrapText(false);
		rankLabel.setUnderline(true);

		if (p.isOrdered()) {
			Color color = Markup.UNASSOCIATED_COLORS[1];
			medianArrow = new MedianArrow(color, color, 7, 0);
		}
	}

	void delayedInit() {
		if (percentLabels == null) {
			percentLabels = new APText[3];
			boolean visible = rankLabel.getVisible();
			// double percentLabelScaledW = percentLabelW * PERCENT_LABEL_SCALE;
			// double x = Math.round(-percentLabelScaledW);
			for (int i = 0; i < 3; i++) {
				percentLabels[i] = art().oneLineLabel();
				percentLabels[i].setTransparency(0);
				percentLabels[i].setVisible(visible);
				percentLabels[i].setPickable(false);
				percentLabels[i].setTextPaint(Bungee.PERCENT_LABEL_COLOR);
				percentLabels[i].setJustification(Component.RIGHT_ALIGNMENT);
				percentLabels[i].setConstrainWidthToTextWidth(false);
				// percentLabels[i].setWidth(percentLabelW);
				// percentLabels[i].setScale(percentLabelScale);
				// percentLabels[i].setXoffset(x);
				front.addChild(percentLabels[i]);
			}
			percentLabels[0].setText("0%");
			percentLabels[1].setText("100%");
			percentLabels[2].setText("100%");

			percentLabelHotZone = new LazyPNode();
			// percentLabelScaledW *= 0.67; // Make hot zone cover 100%, rather
			// // than up to 0.001%
			// percentLabelHotZone.setBounds(-percentLabelScaledW, 0,
			// percentLabelScaledW, 1);
			// front.addChild(percentLabelHotZone);
			percentLabelHotZone.setPickable(false);
			// percentLabelHotZone.setPaint(Color.yellow);
			percentLabelHotZone
					.addInputEventListener(new HotZoneListener(this));

			hotLine = new LazyPNode();
			hotLine.setPaint(Bungee.PERCENT_LABEL_COLOR);
			hotLine.setVisible(false);
			hotLine.setPickable(false);
		}
	}

	String getName() {
		return p.getName();
	}

	void updateData() {
		// Util.print("PV.updateData " + getName() + " " + curtainH + " "
		// + fold.clip.getY());
		assert rank.expectedPercentOn() >= 0;
		if (query().isQueryValid()) {
			for (Iterator it = barTable.values().iterator(); it.hasNext();) {
				Bar bar = ((Bar) it.next());
				bar.updateData();
			}
		}
		if (medianArrow != null)
			layoutMedianArrow();
		drawLabels();
	}

	void animateData(double zeroToOne) {
		for (Iterator it = barTable.values().iterator(); it.hasNext();) {
			Bar bar = ((Bar) it.next());
			bar.animateData(zeroToOne);
		}
	}

	void setBarTransparencies(float zeroToOne) {
		for (Iterator it = barTable.values().iterator(); it.hasNext();) {
			Bar bar = ((Bar) it.next());
			bar.setTransparency(zeroToOne);
		}
	}

	// Called only by Rank.redraw.
	void validate(int visibleWidth, boolean isShowRankLabels) {
		if (p.isPrefetched() || p.getParent() == null) {
			// Util.print("pv.validate " + p + " " + visibleWidth + " "
			// + p.getTotalCount() + " " + p.getTotalChildTotalCount());
			// loseLabels();
			// layoutPercentLabels();
			setPercentLabelVisible();
			leftEdge = 0;
			// if (logicalWidth < visibleWidth)
			logicalWidth = visibleWidth;
			// rightEdge = _w;

			// need to redraw for the sake of labels if queryW changed (and
			// therefore labels height changed), even if our width didn't
			// change.
			//
			// boolean changeW = _w != w;
			// if (changeW) {
			front.setWidth(visibleWidth);
			front.reset();
			front.moveTo(0, 1);
			front.lineTo(0, 0);
			front.lineTo(visibleWidth, 0);
			front.lineTo(visibleWidth, 1);
			parentRect.setWidth(visibleWidth);
			setWidth(visibleWidth);
			revalidate();
			layoutLightBeam();
			rankLabel.setVisible(isShowRankLabels);
			// }
		} else {
			queuePrefetch();
		}
	}

	void revalidate() {
		// Util.print("pv.revalidate " + p + " " + _w + " " + w);
		// front.setBounds(0, 0, (int) (rightEdge - leftEdge), 1);
		drawBars();
		drawLabels();
		if (medianArrow != null) {
			// Do this after drawBars, as offset computation uses bar offsets
			medianArrow.setOffset(logicalWidth / 2 - leftEdge, 1.0);
			if (query().isQueryValid())
				layoutMedianArrow();
		}
	}

	void queuePrefetch() {
		List v = new Vector(2);
		v.add(p);
		v.add(getDoValidate());
		query().queuePrefetch(v);
	}

	void setPercentLabelVisible() {
		if (percentLabels != null) {
			boolean isShowRankLabels = this == rank.perspectives[0];
			// percentScale.setVisible(isShowRankLabels);
			percentLabels[0].setVisible(isShowRankLabels);
			percentLabels[1].setVisible(isShowRankLabels);
			percentLabels[2].setVisible(isShowRankLabels
			// && rank.expectedPercentOn() < 1
					);
			// hotLine.setWidth(w * summary.selectedFrontH);
		}
	}

	private transient Runnable doValidate;

	Runnable getDoValidate() {
		if (doValidate == null)
			doValidate = new Runnable() {

				public void run() {
					rank.validateInternal();
				}
			};
		return doValidate;
	}

	void layoutMedianArrow() {
		if (p.isPrefetched()) {
			if (medianArrow.unconditionalMedian == null) {
				medianArrow.unconditionalMedian = p.getMedianPerspective(false);
			}
			double median = p.median(true);
			if (median >= 0.0) {
				int medianIndex = (int) median;
				double childFraction = median - medianIndex;
				Perspective medianChild = p.getNthChild(medianIndex);
				medianArrow.conditionalMedian = medianChild;
				Bar bar = (Bar) barTable.get(medianChild);
				while (bar == null && medianIndex < p.nChildren() - 1) {
					childFraction = 0.0;
					bar = (Bar) barTable.get(p.getNthChild(++medianIndex));
				}
				if (bar != null) {
					double x = bar.getX() + childFraction * bar.getWidth();
					double length = x - medianArrow.getXOffset();
					medianArrow.setLength((int) length);
					// Color color = null;
					medianArrow.updateColor(p.medianTestSignificant());
					// Util.print("median: " + p + " " + median + " " +
					// bar
					// + " " + x + " " + length);
				}
				medianArrow.moveToFront();
			}
		}
	}

	// called as a result of changing deselectedFrontH, etc
	void layoutPercentLabels() {
		if (isConnected()) {
			// double x = Math.round(-percentLabelW * PERCENT_LABEL_SCALE);

			double frontH = summary.selectedFrontH();
			double yOffset = -PERCENT_LABEL_SCALE * art().lineH / 2.0 / frontH;
			// Util.print("percent y offset = " + yOffset);
			double x = percentLabels[0].getXOffset();
			double scaleY = PERCENT_LABEL_SCALE / frontH;
			percentLabels[0].setTransform(Util.scaleNtranslate(
					PERCENT_LABEL_SCALE, scaleY, x, 1.0 + yOffset));
			percentLabels[1].setTransform(Util.scaleNtranslate(
					PERCENT_LABEL_SCALE, scaleY, x, 0.5 + yOffset));
			percentLabels[2].setTransform(Util.scaleNtranslate(
					PERCENT_LABEL_SCALE, scaleY, x, yOffset));
			// scaleMedianArrow();
			// edu.cmu.cs.bungee.piccoloUtils.gui.Util.printDescendents(percentLabels[2]);
		}
	}

	void scaleMedianArrow() {
		if (medianArrow != null) {
			double scaleY = 1.0 / front.goalYscale; // 1.0 /
			// summary.selectedFrontH;
			medianArrow.setTransform(Util.scaleNtranslate(1.0, scaleY,
					medianArrow.getXOffset(), 1.0)); // -
			// medianArrow.getHeight()
			// * scaleY / 2.0));
		}
	}

	void hotZone(double y) {
		double effectiveY = 1.0 - y;
		if (rank.warpPower() == 1.0) {
			y = Math.max(y, 0.5);
			effectiveY = 2.0 - 2.0 * y;
		}
		double percent = rank.unwarp(effectiveY);
		// Util.print("hotZone " + y + " " + rank.expectedPercentOn() + " " +
		// rank.warpPower() + " " + percent);
		double frontH = summary.selectedFrontH();
		double offset = -art().lineH / 2.0 / frontH;
		double scaleY = PERCENT_LABEL_SCALE / frontH;
		percentLabels[1].setTransform(Util.scaleNtranslate(PERCENT_LABEL_SCALE,
				scaleY, percentLabels[1].getXOffset(), y + offset));
		percentLabels[1].setText(ResultsGrid.formatPercent(percent, null)
				.toString());
		hotLine.setVisible(true);
		hotLine.moveToFront();
		hotLine.setScale(1 / frontH);
		hotLine.setBounds(0, (int) (y * frontH), logicalWidth * frontH, 1);
		// Util.print(hotLine.getGlobalBounds());
		// gui.Util.printDescendents(hotLine);
	}

	void loseHotZone() {
		double frontH = summary.selectedFrontH();
		double offset = -art().lineH / 2.0 / frontH;
		double scaleY = PERCENT_LABEL_SCALE / frontH;
		percentLabels[1].setTransform(Util.scaleNtranslate(PERCENT_LABEL_SCALE,
				scaleY, percentLabels[1].getXOffset(), 0.5 + offset));
		percentLabels[1].setText(ResultsGrid.formatPercent(
				rank.expectedPercentOn(), null).toString());
		hotLine.setVisible(false);
	}

	// double getFoldH() {
	// double h = getHeight();
	// double frontH = Util.min(h, summary.perspectiveSelectedFrontH);
	// double extra = h - frontH;
	// double ratio = summary.foldH / summary.perspectiveLabelH;
	// double texth = extra / (1.0 + ratio);
	// double foldH = h - frontH - texth;
	// return foldH;
	// }

	public void layoutChildren() {
		if (logicalWidth > 0) { // Make sure we've been initialized.
			double h = getHeight();
			if (!rank.componentHeights.equals(prevComponentHeights) /*
																	 * ||
																	 * Math.abs(h -
																	 * prevH) >
																	 * 1.0
																	 */) {
				// assert rightEdge - leftEdge == getWidth();
				// Util.print("pv.layoutChildren " + p + " " + h + " "
				// + rank.foldH() + " " + rank.frontH());
				prevComponentHeights = rank.componentHeights;
				prevH = h;
				front.layout(rank.foldH(), rank.frontH());
				scaleMedianArrow();
				double yScale = rank.labelsH();
				if (yScale > 0)
					// Avoid division by zero
					yScale /= summary.selectedLabelH();
				labels.layout(rank.foldH() + rank.frontH(), yScale);
				layoutRankLabel(false);
				layoutPercentLabels();
				if (!areLabelsInited())
					drawLabels(); // In case labels just now became visible
				layoutLightBeam();
				// If a child has a light beam and we are connected, and then
				// you connect to something above us,
				// child bounds won't change, but the light beam needs to.
				PerspectiveViz[] children = getChildPVs();
				if (children != null) {
					for (int i = 0; i < children.length; i++) {
						children[i].layoutLightBeam();
					}
				}
			}
		}
	}

	void layoutRankLabel(boolean contentChanged) {
		// Util.print("layoutRankLabel " + p + " " + rankLabel.getVisible() + "
		// "
		// + art().query.isQueryValid());
		if (rankLabel.getVisible() && rank.frontH() > 0) {
			double xScale = 1.0;
			double yScale = 1.0 / rank.frontH();
			double labelH = rank.frontH() + rank.labelsH();
			double lineH = art().lineH;
			if (labelH < lineH) {
				xScale = labelH / lineH;
				yScale *= xScale;
				labelH = lineH;
			} else
				labelH = lineH * ((int) (labelH / lineH));
			boolean boundsChanged = rankLabel.setBounds(0, 0, Math.floor(rank
					.rankLabelWdith()
					/ xScale), labelH);
			rankLabel.setTransform(Util.scaleNtranslate(xScale, yScale,
					rankLabel.getXOffset(), 0.0));
			if (contentChanged || boundsChanged) {
				rankLabel.setHeight(labelH);
				rankLabel.layoutBestFit();
				// Noone else should call layout, because notifier will be
				// lost!!!
				hackRankLabelNotifier();
			}
		}
	}

	/**
	 * Add this PerspectiveViz as the pickFacetTextNotifier of all rankLabel
	 * FacetTexts with an actual facet.
	 */
	void hackRankLabelNotifier() {
		for (int i = 0; i < rankLabel.getChildrenCount(); i++) {
			FacetText ft = (FacetText) rankLabel.getChild(i);
			if (ft.getFacet() != null) {
				ft.pickFacetTextNotifier = this;
				// art().validatePerspectiveList();
				break;
			}
		}
	}

	// // Redrawer for rankLabel
	// public void redraw() {
	// layoutRankLabel(true);
	// }
	//
	// void draw(boolean redrawOnly) {
	// // Util.print("pv.draw " + p + " " + redrawOnly);
	// if (w > 0) {
	// // Don't do redraws before we get drawn.
	// drawBars(redrawOnly);
	// redrawLabels();
	// }
	// }
	//
	// void redrawLabels() {
	// loseLabels();
	// drawLabels();
	// }

	/**
	 * [top, bottom, topLeft, topRight, bottomLeft, bottomRight]
	 */
	private float[] prevLightBeamCoords = null;

	/**
	 * Draw the light beam shining from parentPV to this pv.
	 * 
	 * The shape has to change shape during animation, so is called in
	 * layoutChildren rather than draw.
	 */
	void layoutLightBeam() {
		if (parentPV != null) {
			Bar parentFrontRect = parentPV.findFrontRect(p);
			if (parentFrontRect != null) {
				// If we select a range of parent tags, some might be so small
				// that there's not a bar for it.

				PBounds gBottomRect = front.getGlobalBounds();
				if (gBottomRect.getWidth() > 0) {
					float[] newCoords = new float[6];
					Rectangle2D lBottomRect = rank.globalToLocal(gBottomRect);
					newCoords[1] = (float) (lBottomRect.getY()
					// + front .getGlobalBounds().getHeight() / 2
					);
					newCoords[4] = (float) lBottomRect.getX();
					newCoords[5] = newCoords[4]
							+ (float) lBottomRect.getWidth();

					PBounds gTopRect = parentFrontRect.getGlobalBounds();
					Rectangle2D lTopRect = rank.globalToLocal(gTopRect);
					newCoords[0] = (float) (lTopRect.getY() + lTopRect
							.getHeight());
					newCoords[2] = (float) lTopRect.getX();
					newCoords[3] = newCoords[2] + (float) lTopRect.getWidth();

					if (!Arrays.equals(newCoords, prevLightBeamCoords)) {
						prevLightBeamCoords = newCoords;
						if (lightBeam == null) {
							lightBeam = new LazyPPath();
							lightBeam.setPickable(false);
							lightBeam.setStroke(null);
							updateLightBeamTransparency();
							rank.addChild(lightBeam);
						} else {
							lightBeam.reset();
							lightBeam.moveToFront();
						}
						float[] Xs = { newCoords[4], newCoords[2],
								newCoords[3], newCoords[5], newCoords[4] };
						float[] Ys = { newCoords[1], newCoords[0],
								newCoords[0], newCoords[1], newCoords[1] };
						lightBeam.setPathToPolyline(Xs, Ys);
					}
				}
			} else if (lightBeam != null) {
				lightBeam.removeFromParent();
				// rank.removeChild(lightBeam);
				lightBeam = null;
			}
		}
	}

	PerspectiveViz[] getChildPVs() {
		return summary.getChildPVs(this);
	}

	// void initFold() {
	// // Util.print("initFold " + p.getName());
	// assert fold != null;
	// float center = (int) (w / 2.0);
	// Enumeration it = barTable.elements();
	// while (it.hasMoreElements()) {
	// Bar bar = (Bar) it.nextElement();
	// bar.initFold(fold, center);
	// }
	// assert percentLabels != null;
	// // fold.addChild(hLine);
	// // fold.addChild(percentLabels[2]);
	// // fold.addChild(percentLabels[3]);
	// }

	// private boolean foldInitted() {
	// return fold.getChildrenCount() > 0;
	// }

	boolean verifyBarTables() {
		// // printBarXs();
		// // Util.print("PV " + getName() + " has " + barTable.size() + "
		// bars.");
		// Perspective prevFacet = barXs[0];
		// assert prevFacet != null;
		// boolean lastEmpty = false;
		// for (int i = 1; i < w; i++) {
		// if (barXs[i] == null)
		// lastEmpty = true;
		// else if (barXs[i] == prevFacet)
		// lastEmpty = false;
		// else {
		// assert lastEmpty == false : i;
		// prevFacet = barXs[i];
		// }
		// }
		// assert lastEmpty == false;
		return true;
	}

	String printBarXs() {
		// Util.printDeep(barXs);
		ItemPredicate prevFacet = barXs[0];
		int facetStart = 0;
		for (int i = 1; i < logicalWidth; i++) {
			if (barXs[i] != null) {
				if (barXs[i] == prevFacet)
					i++;
				else if (facetStart != i - 1)
					Util.print("\nunterminated:");
				Util.print(prevFacet + ": [" + facetStart + ", " + (i - 1)
						+ "]");
				prevFacet = barXs[i];
				facetStart = i;
			}
		}
		return "";
	}

	private void setTextSize() {
		labelHprojectionW = (int) (1.1 * art().lineH);

		if (percentLabels != null) {
			double percentLabelW = art().getStringWidth("0.001%");
			double percentLabelScaledW = percentLabelW * PERCENT_LABEL_SCALE;
			double x = Math.round(-percentLabelScaledW);
			for (int i = 0; i < 3; i++) {
				percentLabels[i].setWidth(percentLabelW);
				percentLabels[i].setXoffset(x);
			}
			percentLabelScaledW *= 0.67; // Make hot zone cover 100%, rather
			// than up to 0.001%
			percentLabelHotZone.setBounds(-percentLabelScaledW, 0,
					percentLabelScaledW, 1);
		}
	}

	private void drawBars() {
		// Util.print("drawBars " + p + " " + redrawOnly);
		assert p.getTotalChildTotalCount() > 0 : p;
		front.removeAllChildren();
		front.addChild(rankLabel);
		setTextSize();
		if (percentLabels != null) {
			// front.addChild(percentScale);
			for (int i = 0; i < 3; i++) {
				percentLabels[i].setFont(art().font);
				front.addChild(percentLabels[i]);
			}
			front.addChild(percentLabelHotZone);
			front.addChild(hotLine);
			front.addChild(parentRect);
		}
		if (medianArrow != null) {
			front.addChild(medianArrow);
		}
		computeBars();
		assert verifyBarTables();
	}

	int maybeDrawBar(Perspective datum, double divisor, boolean forceDraw) {
		assert datum.getParent() == p;
		int barW = 0;
		if (datum.getTotalCount() > 0
				&& (!forceDraw || lookupBar(datum) == null)) {
			// if !forceDraw, we're drawing all new bars. No need to check
			// table.
			assert p.getTotalChildTotalCount() >= datum.getTotalCount() : datum
					+ " " + datum.getTotalCount() + "/"
					+ p.getTotalChildTotalCount() + " "
					+ query().isQueryValid();
			double maxX = datum.cumCount() * divisor;
			double minX = Math.max(leftEdge, maxX - datum.getTotalCount()
					* divisor)
					- leftEdge;
			maxX = Math.min(getWidth() - 1, maxX - leftEdge);
			assert minX >= 0;
			// if (maxX > minX) {
			int iMaxX = (int) maxX;
			int iMinX = (int) minX;
			// Util.print("maybe draw bar " + p + "." + datum + " "
			// + (datum.cumCount() - datum.getTotalCount()) + "-"
			// + datum.cumCount() + "/" + p.getTotalChildTotalCount()
			// + " " + iMinX + "-" + iMaxX + " " + forceDraw);
			assert datum.cumCount() <= p.getTotalChildTotalCount() : datum
					+ " " + datum.cumCount() + "/"
					+ p.getTotalChildTotalCount();
			assert maxX + 0.99999 <= logicalWidth : minX + " " + maxX + " "
					+ logicalWidth;
			// x_mid_coords[datum.whichChild()] = (iMaxX + iMinX) >>> 1;
			assert iMinX >= 0 : datum + " " + datum.cumCount() + " "
					+ datum.getTotalCount();
			if (barXs[iMinX] != null && barXs[iMinX] != datum) {
				iMinX += 1;
				assert iMinX >= iMaxX || barXs[iMinX] == null
						|| barXs[iMinX] == datum : this + " " + datum + " "
						+ printBarXs() + " " + iMinX + "-" + iMaxX;
			}
			if (barXs[iMaxX] != null && barXs[iMaxX] != datum) {
				iMaxX -= 1;
				assert ((int) minX) >= iMaxX || barXs[iMaxX] == null
						|| barXs[iMaxX] == datum : this + " " + datum + " "
						+ printBarXs();
			}
			if (iMinX > iMaxX && forceDraw) {
				// No space. Try to create some.
				// I turned off these first two options because drawing more
				// bars
				// was hurting performance more than it was helping the
				// user.
				// Any bar that has room for a label will be drawn in
				// initLabels;
				if (iMinX > 1
						&& (barXs[iMinX - 2] == null || barXs[iMinX - 2] == barXs[iMinX - 1])) {
					barXs[iMinX - 2] = barXs[iMinX - 1];
					iMinX -= 1;
					iMaxX = iMinX;
				} else if (iMaxX < barXs.length - 2
						&& (barXs[iMaxX + 2] == null || barXs[iMaxX + 2] == barXs[iMaxX + 1])) {
					barXs[iMaxX + 2] = barXs[iMaxX + 1];
					iMaxX += 1;
					iMinX = iMaxX;
				} else if (forceDraw) {
					iMinX = (iMinX > 1) ? iMinX - 1 : iMaxX + 1;
					iMaxX = iMinX;
					barTable.remove(barXs[iMinX]);
					// Util.print("Removing bar " + datum);
				}
			}
			if (iMinX <= iMaxX) {
				barW = iMaxX - iMinX + 1;
				// Util.print(" " + iMinX + "-" + iMaxX);
				Bar bar = new Bar(this, iMinX, barW, datum);
				front.addChild(bar);
				if (query().isQueryValid()) {
					double expectedPercentOn = rank.expectedPercentOn();
					if (expectedPercentOn >= 0 && datum.getOnCount() >= 0) {
						// if (foldInitted()) {
						// bar.initFold(fold, w / 2);
						// assert forceDraw : "fold hasn't been initted on
						// initial
						// drawBars";
						// double exp = getExp();
						bar.updateData();
						if (true || !forceDraw)
							bar.animateData(1.0);
						// }
					}
				}
				barTable.put(datum, bar);
				assert validateBarX(datum, iMinX);
				assert validateBarX(datum, iMaxX);
			}
		}
		return barW;
	}

	double barWidthRatio() {
		return (logicalWidth - 0.999999) / p.getTotalChildTotalCount();
	}

	Bar lookupBar(Perspective facet) {
		Bar result = (Bar) barTable.get(facet);
		// if (result != null)
		// Util.print("lookupBar " + facet + " => " + result.facet);
		return result;
	}

	int nBars() {
		// Util.print("PV.nBars " + barTable.size());
		return barTable.size();
	}

	double[] pValues() {
		double[] result = new double[nBars()];
		int i = 0;
		for (Iterator it = barTable.keySet().iterator(); it.hasNext(); i++) {
			ItemPredicate facet = (ItemPredicate) it.next();
			result[i] = facet.pValue();
			// Util.print(p + " " + result[i]);
			// if (result[i]<0)
			// result[i] = 1.0;
		}
		return result;
	}

	private boolean validateBarX(Perspective datum, int xCoord) {
		double divisor = barWidthRatio();
		double maxX = datum.cumCount() * divisor;
		double minX = maxX - datum.getTotalCount() * divisor;
		int iMaxX = (int) (maxX - leftEdge);
		int iMinX = (int) (minX - leftEdge);
		assert xCoord >= iMinX && xCoord <= iMaxX : xCoord + " [" + iMinX
				+ ", " + iMaxX + "]";
		return true;
	}

	private FacetPText mouseNameLabel;

	void updateSelection(Perspective facet) {
		updateLightBeamTransparency();

		drawMouseLabel();

		Bar bar = lookupBar(facet);
		if (bar != null) {
			bar.updateSelection();
			// drawLabels();
			if (labels != null && labels.getVisible()) {
				for (int i = 0; i < labels.getChildrenCount(); i++) {
					FacetPText label = (FacetPText) labels.getChild(i);
					if (label.getFacet() == facet)
						label.setColor();
				}
			}
		}
	}

	void updateLightBeamTransparency() {
		if (lightBeam != null) {
			lightBeam
					.setPaint(p.isRestriction(true) ? Markup.INCLUDED_COLORS[2]
							: Color.white);
			if (art().highlightedFacet == p)
				lightBeam.setTransparency(0.4f);
			else
				lightBeam.setTransparency(0.2f);
		}
	}

	/**
	 * y coordinate for numeric labels; depends on maxCount.
	 */
	double numW;

	double nameW;

	// void loseLabels() {
	// // Util.print("PV.loseLabels " + p + " " + labelXs);
	// if (labelXs != null) {
	// for (int i = 0; i < labelXs.length; i++)
	// labelXs[i] = null;
	// }
	// labels.removeAllChildren();
	// }

	private void drawLabels() {
		assert SwingUtilities.isEventDispatchThread();
		// Util.print("initLabels " + p + " "
		// + areLabelsInited() + " " + barTable.size() + " " +
		// p.getOnCount());

		// even if we're not connected, make sure stale labels don't come back
		// later
		labels.removeAllChildren();
		if (labels.getVisible() && barTable.size() > 0
				&& query().isQueryValid()) {
			// If there aren't any bars, x_mid_coords hasn't been initialized.
			// This is a problem inside rank.redraw after addPerspective.
			// Util.print("PV.initLabels " + p);
			if (p.isPrefetched()) {
				// summary.q.prefetchData(p);
				int maxCount = rank.maxCount();
				numW = Math.round(art().numWidth(maxCount) + 10.0);
				nameW = Math.floor(summary.selectedLabelH() * 1.4 - numW
						- art().lineH / 2) - 10;

				// int freeLabelXs = w + 2;
				// SortedSet allRestrictions = p.allRestrictions();
				// for (Iterator it = allRestrictions.iterator(); it.hasNext();)
				// {
				// Perspective facet = (Perspective) it.next();
				// maybeDrawLabel(facet);
				// }

				computeLabels();

				// // If sort order was really important we should synchronize
				// p.sortDataIndexByOn();
				// // Util.print("initLabels " + p.sortOrder() + " " + p);
				// for (int i = 0; i < p.nChildren() && freeLabelXs > 0; i++) {
				// Perspective child = p.getNthOnValue(i);
				// // Util.print(child + " " + child.onCount + " " + maxCount);
				// freeLabelXs -= maybeDrawLabel(child);
				// }
				// // Util.print("initLabels done " + p.sortOrder() + " " + p);

				mouseNameLabel = new FacetPText(null, 0.0, -1.0);
				mouseNameLabel.setVisible(false);
				mouseNameLabel.setPickable(false);
				labels.addChild(mouseNameLabel);
			} else {
				queuePrefetch();
			}
			drawMouseLabel();
		}
	}

	private boolean areLabelsInited() {
		return labels.getChildrenCount() > 0;
	}

	// void drawLabels() {
	// if (labels.getVisible()) {
	// // if (p.isPrefetched()) {
	// // Util.print("drawLabels "
	// // + p
	// // + " "
	// // + (mouseNameLabel == null ? false : mouseNameLabel
	// // .getVisible()));
	// initLabels();
	// drawMouseLabel();
	// // } else {
	// // summary.fetcher.add(this);
	// // }
	// }
	// }

	private int prevMidX;

	private void drawMouseLabel() {
		// Util.print("drawMouseLabel " + p + "\n" + v);
		if (areLabelsInited()) {
			Perspective mousedFacet = art().highlightedFacet;
			if (mousedFacet != null && mousedFacet.getParent() == p) {
				drawMouseLabelInternal(mousedFacet);
			} else if (mouseNameLabel.getVisible()) {
				drawMouseLabelInternal(null);
			}
		}
	}

	private void drawMouseLabelInternal(Perspective v) {
		boolean state = v != null;
		int midX = state ? midLabelPixel(v, barWidthRatio()) : prevMidX;
		int frontW = (int) getWidth();
		if (midX >= 0 && midX < frontW) {
			prevMidX = midX;

			int minX = Util.constrain(midX - labelHprojectionW, 0, frontW - 1);
			int maxX = Util.constrain(midX + labelHprojectionW, 0, frontW - 1);
			for (int i = minX; i <= maxX; i++) {
				FacetText label = findLabel(labelXs[i]);
				if (label != null) {
					int midLabel = midLabelPixel(labelXs[i], barWidthRatio());
					if (Math.abs(midLabel - midX) <= labelHprojectionW) {
						// Util.print("need mouseLabel? " + p + " " + v + " " +
						// " "
						// + labelXs[i].getFacet() + " "
						// + labelXs[i].getVisible() + " "
						// + mouseNameLabel.getVisible());
						if (labelXs[i] == v) {
							label.setVisible(true);
							mouseNameLabel.setVisible(false);
							return;
						} else
							label.setVisible(v == null);
					}
				}
			}

			if (state) {
				maybeDrawBar(v, barWidthRatio(), true);
				assert verifyBarTables();
				mouseNameLabel.setFacet(v);
				mouseNameLabel.setPTextOffset(midX, 0.0);
				labels.moveToFront();
			} else {
				front.moveToFront();
			}
			mouseNameLabel.setVisible(state);
			mouseNameLabel.setPickable(state);
		}
	}

	FacetText findLabel(Perspective facet) {
		if (facet != null) {
			for (int i = 0; i < labels.getChildrenCount(); i++) {
				FacetText child = (FacetText) labels.getChild(i);
				if (child.facet == facet)
					return child;
			}
		}
		return null;
	}

	private int maybeDrawLabel(Perspective v) {
		int nPixelsOccluded = 0;
		if (v.getTotalCount() > 0) {
			int frontW = (int) getWidth();
			int midX = midLabelPixel(v, barWidthRatio());
			assert midX >= 0 : v;
			assert midX < frontW : v + " " + midX + " " + frontW;
			if (labelXs[midX] == null || labelXs[midX] == v) {
				maybeDrawBar(v, barWidthRatio(), true);
				assert verifyBarTables();
				if (lookupBar(v) != null) {
					FacetPText label = getFacetPText(v, 0.0, midX);
					labels.addChild(label);

					int minX = Util.constrain(midX - labelHprojectionW, 0,
							frontW - 1);
					int maxX = Util.constrain(midX + labelHprojectionW, 0,
							frontW - 1);
					for (int i = minX; i <= maxX; i++) {
						if (labelXs[i] == null)
							nPixelsOccluded++;
						labelXs[i] = v;
					}
					// Util.print("maybeDrawLabel " + v + " " + minX + " - " +
					// maxX);
				}
			}
		}
		return nPixelsOccluded;
	}

	boolean isConnected() {
		return rank.isConnected();
	}

	void connectToPerspective() {
		rank.connect();
	}

	void setConnected(boolean connected) {
		// Util.print("PV.setConnected " + p.getName() + " " + connected);
		if (rankLabel.getVisible()) {
			float transparency = connected ? 1 : 0;
			for (int i = 0; i < 3; i++) {
				percentLabels[i].animateToTransparency(transparency,
						Bungee.dataAnimationMS);
			}
			if (query().isQueryValid())
				redraw100PercentLabel();
			percentLabelHotZone.setPickable(connected);
			rankLabel.setPickable(connected);
		}
	}

	// Visibiility is determined by whether we are rank's first perspective (in
	// validate).
	// and also by parent's visibility (the fold).
	// Transparency and position are determined by curtainH
	void redraw100PercentLabel() {
		if (isConnected() && rank.totalChildTotalCount() >= 0) {
			// Util.print("PV.redrawPercents " + p.getName() + " " + curtainH +
			// " "
			// + (Art.lineH / summary.foldH));
			// assert curtainH <= 1;
			// double foldH = summary.selectedFoldH;
			// double lineH = summary.art.lineH;
			double percent = rank.expectedPercentOn();
			percentLabels[1].setText(ResultsGrid.formatPercent(percent, null)
					.toString());
			percentLabels[1].setVisible(percent < 1);
		}
	}

	// void drawPercentScale() {
	// double w = percentLabelW;
	// // double exp = 1 / getExp();
	// float[] Xs = new float[12];
	// float[] Ys = new float[12];
	// int i = 0;
	// for (double y = 0; y <= 1; y += 0.1) {
	// double percent = rank.unwarp(1 - y);
	// Ys[i] = (float) (rank.warpPower() == 1 ? y / 2 + 0.5 : y);
	// Xs[i] = (float) (w * (1 - percent));
	// i++;
	// }
	// Xs[11] = Xs[10];
	// Ys[11] = Ys[0];
	// // Util.print("");
	// // Util.printDeep(Xs);
	// // Util.printDeep(Ys);
	// // gui.Util.printDescendents(percentScale);
	// percentScale.setPathToPolyline(Xs, Ys);
	// }

	// private void updatePercentLabel(APText label, float transparency, double
	// x,
	// double y) {
	// // label.moveToFront();
	// // if (transparency == 1)
	// // Util.print("updatePercentLabel " + p + " " + y);
	// boolean shouldAnimate = (isConnected() || label.getParent() != null
	// && label.getParent().getVisible())
	// && (transparency > 0 || label.getTransparency() > 0);
	// if (shouldAnimate) {
	// if (x != label.getXOffset() || y != label.getYOffset())
	// label.animateToPositionScaleRotation(x, y, percentLabelScale,
	// 0, Art.dataAnimationMS);
	// if (transparency != label.getTransparency())
	// label.animateToTransparency(transparency, Art.dataAnimationMS);
	// } else {
	// label.setOffset(x, y);
	// label.setTransparency(transparency);
	// }
	// }

	// boolean isRestricted() {
	// return p.isRestricted();
	// }

	// String[] getRestrictionNames(boolean isLocalOnly) {
	// return p.getRestrictionNames(isLocalOnly);
	// }

	// Called by Bar.highlight
	void highlightFacet(Perspective facet, int modifiers, PInputEvent e) {
		// Util.print("PV.highlightFacet " + facet);
		if (Query.isEditable && e.isRightMouseButton()) {
			art().setClickDesc("Set selected for edit");
		} else if (Query.isEditable && e.isMiddleMouseButton()) {
			art().setClickDesc("Open edit menu");
		} else if (isConnected() || facet == null) {
			art()
					.setClickDesc(
							facet != null ? facet.facetDoc(modifiers) : null);
		} else {
			highlight(facet, modifiers);
		}
		art().highlightFacet(facet, modifiers);
	}

	// Called as a result of pickFacetTextNotifier on rank label FacetTexts
	public boolean pick(FacetText node, int modifiers) {
		// Util.print("PV.pick " + p + " " + node + " " + modifiers + " "
		// + node.isPickable);
		return pickFacet(node.getFacet(), modifiers);
	}

	// Called as a result of pickFacetTextNotifier on rank label FacetTexts
	boolean pickFacet(Perspective facet, int modifiers) {
		// Util.print("PV.pick " + p + " " + node + " " + modifiers + " "
		// + node.isPickable);
		// Skip if over checkboxes and therefore modifiers != 0 (should not
		// happen for top-level ranks)
		boolean handle = isHandlePickFacetText(facet, modifiers);
		if (handle) {
			art().printUserAction(Bungee.RANK_LABEL, facet, modifiers);
			if (!isConnected()) {
				connectToPerspective();
			} else {
				if (art().arrowFocus != null
						&& art().arrowFocus.getParent() == p)
					facet = art().arrowFocus;
				else if (p.nRestrictions() > 0)
					facet = (Perspective) p.allRestrictions().first();
				else
					facet = p.getNthChild(0);
				summary.ensurePerspectiveList(facet).toggle();
			}
		}
		return handle;
	}

	// // Called as a result of pickFacetTextNotifier on rank label FacetTexts
	// public boolean mouseMoved(FacetText node, int modifiers) {
	// // Avoid calling updateItemPredicateClickDesc
	// return highlight(node, true, modifiers);
	// }
	//
	// // Called as a result of pickFacetTextNotifier on rank label FacetTexts
	// public boolean shiftKeysChanged(FacetText node, int modifiers) {
	// // Avoid calling updateItemPredicateClickDesc
	// return highlight(node, true, modifiers);
	// }

	// Called as a result of pickFacetTextNotifier on rank label FacetTexts
	public boolean highlight(FacetText node, boolean state, int modifiers) {
		Perspective facet = state ? node.getFacet() : null;
		return highlight(facet, modifiers);
	}

	boolean isHandlePickFacetText(Perspective facet, int modifiers) {
		return facet != null
				&& (modifiers == 0 && facet == p || !isConnected());
	}

	boolean highlight(Perspective facet, int modifiers) {
		boolean handle = isHandlePickFacetText(facet, modifiers);
		// Util.print("PV.highlight " + modifiers + " " + handle);
		if (handle) {

			// Highlight the facet, but we'll do our own mouse doc.
			art().highlightFacet(facet, modifiers);

			Markup doc = Query.emptyMarkup();
			if (!isConnected()) {
				doc.add("open category ");
				doc.add(p);
			} else if (summary.perspectiveList == null
					|| summary.perspectiveList.isHidden()) {
				doc.add("List all ");
				doc.add(p);
				doc.add(" tags");
			} else
				doc.add("Hide the list of tags");
			art().setClickDesc(doc);
		}
		return handle;
	}

	double frontBottomOffset() {
		return front.getYOffset()
				+ Util.min(Math.round(front.getFullBounds().getHeight()), rank
						.getHeight());
	}

	void hidePvTransients() {
		summary.mayHideTransients();
	}

	Hashtable facetPTexts = new Hashtable();

	FacetPText getFacetPText(Perspective _facet, double _y, double x) {
		FacetPText label = null;
		if (_facet != null)
			label = (FacetPText) facetPTexts.get(_facet);
		if (label == null || label.numW != numW || label.nameW != nameW) {
			label = new FacetPText(_facet, _y, x);
			if (_facet != null)
				facetPTexts.put(_facet, label);
		} else {
			label.setPTextOffset(x, _y);
			((APText) label).setText(label.art.facetLabel(_facet, numW, nameW,
					false, true, label.showCheckBox, true, label));
			label.setColor();
		}
		return label;
	}

	final class FacetPText extends FacetText {

		void setFacet(Perspective _facet) {
			facet = _facet;
			assert facet.getParent() != null;
			((APText) this).setText(art.facetLabel(facet, numW, nameW, false,
					true, true, true, this));

		}

		// should be FacetText.setFacet
		void setPTextOffset(double x, double y) {
			// Util.print("FacetPText init " + facet);
			setColor();

			double offset = getWidth() / 1.4;
			x = Math.round(x - offset + (y + art.lineH) / 1.4 - 0.85
					* art.lineH);
			double _y = Math.round(y / 1.4 + offset);
			setOffset(x, _y);
		}

		FacetPText(Perspective _facet, double _y, double x) {
			super(summary.art, PerspectiveViz.this.numW,
					PerspectiveViz.this.nameW);
			setRotation(-Math.PI / 4.0);
			// setPaint(Art.summaryBG);

			showCheckBox = true;
			// isPickable = showCheckBox;
			setUnderline(true /* isPickable */);
			if (_facet != null) {
				setFacet(_facet);
				setPTextOffset(x, _y);
				// if (facet == art.highlightedFacet) {
				// // MouseLabel does highlighting for these, so turn it off
				// // for base label.
				// if (_facet.isRestriction())
				// setTextPaint(Art.whites[1]);
				// else
				// setTextPaint(Art.whites[0]);
				// }
			}
			// addInputEventListener(Art.facetClickHandler);
		}

		public boolean pick(PInputEvent e) {
			// Util.print("FacetPNode.pick " + e.getPosition() + " " +
			// e.getPositionRelativeTo(this));
			// int modifiers = e.getModifiers();
			// double x = e.getPositionRelativeTo(this).getX();
			// if (x < art.checkBoxWidth) {
			// modifiers |= InputEvent.CTRL_DOWN_MASK;
			// }
			// // Util.print(p.getFacetTypeName() + " facet picked: " + facet +
			// " "
			// // + facet.getName());
			// art.printUserAction(Art.BAR_LABEL, facet, modifiers);
			if (isConnected()) {
				super.pick(e);
				// toggleFacet(facet, modifiers);
				// p.art.printUserAction("FacetPText.picked: " +
				// p.summary.q.getFacetName(facet));
			} else
				connectToPerspective();
			return true;
		}

		public boolean highlight(boolean state, int modifiers, PInputEvent e) {
			// Util.print("PV.FacetPText.highlight " + p + "."
			// + facet + " " + state + " "
			// + (art.highlightedFacet==null ? null :
			// art.highlightedFacet) + " " + mouseNameLabel.getPickable());
			Point2D mouseCoords = e.getPositionRelativeTo(this);
			// Util.print(mouseCoords);
			double x = mouseCoords.getX();
			double _y = mouseCoords.getY();
			modifiers = getModifiersInternal(modifiers, x);
			if (state) {
				// Workaround Piccolo rotated-selection bug by
				// skipping redundant calls and checking for erroneous ones.
				if (art.highlightedFacet != facet) {
					if (x >= -5.0 && x <= getWidth() + 5 && _y >= -5.0
							&& _y <= getHeight() + 5.0) {
						highlight(state, modifiers);
					}
				}
			} else if (art.highlightedFacet == facet) {
				highlight(state, modifiers);
			}
			return true;
		}
	}

	// public boolean pick(FacetText node, int modifiers) {
	// boolean result = !isConnected();
	// if (result)
	// connectToPerspective();
	// return result;
	// }
	//
	// public boolean highlight(FacetText node, boolean state, int modifiers,
	// PInputEvent e) {
	// Point2D mouseCoords = e.getPositionRelativeTo(this);
	// // Util.print(mouseCoords);
	// double x = mouseCoords.getX();
	// double _y = mouseCoords.getY();
	// modifiers = node.getModifiersInternal(modifiers, x);
	// if (state) {
	// // Workaround Piccolo rotated-selection bug by
	// // skipping redundant calls and checking for erroneous ones.
	// if (art.highlightedFacet != node.facet) {
	// if (x >= -5.0 && x <= getWidth() + 5 && _y >= -5.0
	// && _y <= getHeight() + 5.0) {
	// return false;
	// }
	// }
	// } else if (art.highlightedFacet == node.facet) {
	// return false;
	// }
	// return true;
	// }

	Bar findFrontRect(Perspective facet) {
		Bar result = null;
		Bar bar = lookupBar(facet);
		if (bar != null)
			result = bar;
		return result;
	}

	void clickBar(Perspective facet, int modifiers) {
		findFrontRect(facet).pick(modifiers);
	}

	public String toString() {
		return "<PerspectiveViz " + p + ">";
	}

	void restrict() {
		// if (//query().usesPerspective(p) &&
		// p.restrictData())
		// During a restrict, our parent may have removed us,
		// and calling restrictData would wrongly resetData to -1
		// if (query().usesPerspective(p))
		drawBars();
		drawLabels();
	}

	void startDrag(Point2D ignore, Point2D local) {
		assert Util.ignore(ignore);
		dragStartOffset = local.getX();
		// Util.print(dragStartOffset);
	}

	private double dragStartOffset;

	void drag(Point2D ignore, PDimension delta) {
		assert Util.ignore(ignore);
		// int viewW = (int) (rightEdge - leftEdge);
		double vertical = delta.getHeight();
		double horizontal = delta.getWidth();
		// If you want to just pan, zooming screws you up, and vice-versa, so
		// choose one or the other.
		if (Math.abs(vertical) > Math.abs(horizontal))
			horizontal = 0;
		else
			vertical = 0;
		double deltaZoom = Math.pow(2, -vertical / 20.0);
		int newLogicalWidth = (int) Math.round(logicalWidth * deltaZoom);
		if (newLogicalWidth < getWidth()) {
			leftEdge = 0;
			// rightEdge = viewW;
			newLogicalWidth = (int) getWidth();
		} else {
			// recalculate zoom after rounding newLogicalWidth
			deltaZoom = newLogicalWidth / (double) logicalWidth;
			double pan = -horizontal;
			leftEdge = (int) Util.constrain(leftEdge + pan
					+ (leftEdge + dragStartOffset) * (deltaZoom - 1), 0,
					newLogicalWidth - getWidth());
			// rightEdge = leftEdge + viewW;
		}
		logicalWidth = newLogicalWidth;
		// Util.print(zoom + " " + viewW + " " + newW + " " + leftEdge + " " +
		// rightEdge);
		revalidate();
	}

	final class MedianArrow extends Arrow {

		final MedianArrowHandler medianArrowHandler = new MedianArrowHandler();

		ItemPredicate unconditionalMedian;

		ItemPredicate conditionalMedian;

		int significant = 0;

		int highlighted = 1;

		MedianArrow(Paint headColor, Paint tailColor, int tailDiameter,
				int length) {
			super(headColor, tailColor, tailDiameter, length);
			setPickable(false);
			// line.setPickable(false);
			leftHead.addInputEventListener(medianArrowHandler);
			rightHead.addInputEventListener(medianArrowHandler);
			tail.addInputEventListener(medianArrowHandler);
			line.addInputEventListener(medianArrowHandler);
		}

		void updateColor(int _significant) {
			significant = _significant;
			redraw();
		}

		void redraw() {
			Color color = Bungee.significanceColor(significant, highlighted);
			// Color[] colors = significant == 0 ? Markup.whites
			// : (significant > 0 ? Markup.blues : Markup.oranges);
			// Color color = colors[highlighted];
			setHeadColor(color);
			setTailColor(color);
		}

		void highlight(boolean state) {
			highlighted = state ? 2 : 1;
			redraw();
		}

		final class MedianArrowHandler extends MyInputEventHandler {

			MedianArrowHandler() {
				super(PPath.class);
			}

			protected boolean exit(PNode node) {
				art().showMedianArrowDesc(null);
				highlight(false);
				return true;
			}

			protected boolean enter(PNode ignore) {
				// boolean unconditional = node == tail;
				// Perspective median = unconditional ? unconditionalMedian
				// : conditionalMedian;
				// // Util.print(median);
				// if (median != null) {
				// StringBuffer buf = new StringBuffer();
				// buf.append("The median ");
				// buf.append(median.getFacetType().getName());
				// if (unconditional)
				// buf.append(" is ");
				// else
				// buf.append(" satisfying the query is ");
				// buf.append(median.getName());
				// if (!unconditional) {
				// buf.append(" ");
				// MouseDoc.formatPvalue(p.medianTest(), buf);
				// }
				// Vector desc = new Vector();
				// desc.add(buf.toString());
				art().showMedianArrowDesc(p);
				// }
				highlight(true);
				return true;
			}
		}

	}

	Bungee art() {
		return summary.art;
	}

	Query query() {
		return summary.art.query;
	}

	LazyPNode anchorForPopup(Perspective facet) {
		LazyPNode bar;
		if (facet == p) {
			bar = rankLabel;
		} else {
			if (areLabelsInited())
				drawMouseLabelInternal(facet);
			else if (barTable.size() > 0)
				maybeDrawBar(facet, barWidthRatio(), true);
			bar = findFrontRect(facet);
		}
		// assert bar != null;
		// if (bar != null) {
		// // facetDesc.moveToFront();
		// // breakAtColon(facetDesc);
		// facetDesc.setAnchor(bar);
		// // facetDesc.layout(summary.getWidth(),
		// // getGlobalTranslation().getY());
		// facetDesc.update(facet, rank);
		// return true;
		// } else
		// return false;
		return bar;
	}

	void prepareAnimation() {
		// work around display bug - line gets drawn too thick initially after
		// changes, so hide it during animation. The problem still shows up when
		// the popup translates across it.
		if (medianArrow != null) {
			medianArrow.setVisible(false);
		}
		front.setStrokePaint(null);
	}

	void animate(float zeroToOne) {
		if (zeroToOne == 1) {
			if (medianArrow != null) {
				medianArrow.setVisible(true);
			}
			front.setStrokePaint(Bungee.summaryFG.brighter());
		}
	}

	// private void breakAtColon(TextNfacets facetDesc) {
	// Vector v = facetDesc.content;
	// for (ListIterator it = v.listIterator(); it.hasNext();) {
	// Object o = it.next();
	// if (o instanceof String) {
	// String s = (String) o;
	// int n = s.indexOf(':');
	// if (n == 0) {
	// it.remove();
	// it.add(TextNfacets.newlineTag);
	// it.add(s.substring(n + 2, s.length()));
	// } else if (n == s.length() - 1) {
	// it.remove();
	// it.add(s.substring(0, n));
	// it.add(TextNfacets.newlineTag);
	// } else if (n > 0) {
	// it.remove();
	// it.add(s.substring(0, n));
	// it.add(TextNfacets.newlineTag);
	// it.add(s.substring(n + 2, s.length()));
	// }
	// if (n >= 0)
	// break;
	// }
	// }
	// }

	/**
	 * We want to draw bars greedily by totalCount. However there may be 100,000
	 * children, of which only 100 or so might be drawn. barXs keeps the child
	 * with the highest totalCount that maps into each pixel.
	 * 
	 */
	private void computeBars() {
		// Util.print("computeBars " + getWidth());
		barTable.clear();
		double frontW = getWidth();
		assert frontW <= logicalWidth;
		barXs = new Perspective[(int) (frontW)];
		double divisor = barWidthRatio();
		for (Iterator it = p.getChildIterator(); it.hasNext();) {
			Perspective child = (Perspective) it.next();
			int totalCount = child.getTotalCount();
			if (totalCount > 0) {
				int iMaxX = maxBarPixel(child, divisor);
				int iMinX = minBarPixel(child, divisor);
				if (iMaxX >= 0 && iMinX < frontW) {
					if (iMinX < 0)
						iMinX = 0;
					assert iMaxX >= iMinX : child + " " + iMinX + "-" + iMaxX
							+ " " + child.cumCount() + " "
							+ child.getTotalCount() + " " + divisor + " "
							+ leftEdge + "-" + getWidth();
					// Util.print("Adding bar for " + child + " "
					// + (child.cumCount - child.totalCount) + "-"
					// + child.cumCount + "/" +
					// p.getTotalChildTotalCount()
					// + " " + iMinX + "-" + iMaxX);
					assert child.cumCount() <= p.getTotalChildTotalCount() : child
							+ " "
							+ child.cumCount()
							+ " "
							+ p.getTotalChildTotalCount();
					if (totalCount > itemTotalCount(iMinX))
						barXs[iMinX] = child;
					if (iMaxX > iMinX) {
						if (iMaxX > iMinX + 1 && iMinX + 1 < frontW) {
							assert barXs[iMinX + 1] == null;
							barXs[iMinX + 1] = child;
						}
						if (iMaxX < frontW
								&& totalCount > itemTotalCount(iMaxX))
							barXs[iMaxX] = child;
					}
				}
			}
		}
		drawComputedBars();
	}

	private int itemTotalCount(int x) {
		if (barXs[x] == null)
			return 0;
		else
			return barXs[x].getTotalCount();
	}

	/**
	 * @param child
	 * @return give filters priority over other children
	 */
	private int priorityCount(Perspective child) {
		int result = child.getOnCount();
		if (child.isRestriction())
			result += query().getTotalCount();
		return result;
	}

	private int itemOnCount(int x) {
		if (labelXs[x] == null)
			return -1;
		else
			return priorityCount(labelXs[x]);
	}

	private void drawComputedBars() {
		int frontW = (int) getWidth();
		// if ("Genre".equals(p.getName()))
		// Util.print("drawComputedBars " + this + " " + frontW + " " +
		// logicalWidth + " " + leftEdge);
		double divisor = barWidthRatio();
		for (int x = 0; x < frontW;) {
			Perspective toDraw = barXs[x];
			assert toDraw != null : x + "/" + barXs.length + " "
					+ Util.valueOfDeep(barXs);
			int pixels = maybeDrawBar(toDraw, divisor, false);
			// if ("Genre".equals(p.getName()))
			// Util.print(x + " " + pixels + " " + toDraw + " "
			// + minBarPixel(toDraw, divisor) + "-"
			// + maxBarPixel(toDraw, divisor));
			assert pixels > 0;
			x += pixels;
		}
	}

	/**
	 * @param child
	 * @param divisor
	 * @return x offset relative to leftEdge
	 */
	private int maxBarPixel(Perspective child, double divisor) {
		return (int) (child.cumCount() * divisor - leftEdge);
		// maxX = Math.min(rightEdge, maxX) - leftEdge;
		// return (int) maxX;
	}

	/**
	 * @param child
	 * @param divisor
	 * @return x offset relative to left edge
	 */
	private int minBarPixel(Perspective child, double divisor) {
		return (int) ((child.cumCount() - child.getTotalCount()) * divisor - leftEdge);
	}

	/**
	 * @param child
	 * @param divisor
	 * @return x offset of middle of label relative to leftEdge
	 */
	private int midLabelPixel(Perspective child, double divisor) {
		return (minBarPixel(child, divisor) + maxBarPixel(child, divisor)) / 2;
	}

	private void computeLabels() {
		int frontW = (int) getWidth();
		labelXs = new Perspective[frontW];
		double divisor = barWidthRatio();
		for (Iterator it = p.getChildIterator(); it.hasNext();) {
			Perspective child = (Perspective) it.next();
			if (child.getTotalCount() > 0) {
				int iMidX = midLabelPixel(child, divisor);
				if (iMidX >= 0 && iMidX < frontW
						&& priorityCount(child) > itemOnCount(iMidX))
					labelXs[iMidX] = child;
			}
		}
		drawComputedLabel(null, divisor);
	}

	/**
	 * March along pixels, finding the child Perspectives to draw. At each
	 * pixel, you draw the child with the highest count at that pixel, which was
	 * computed above, unless another child with a higher count has a label that
	 * would occlude it, unless IT would be occluded. So you get a recusive
	 * test, where a conflict on the rightmost label can propagate all the way
	 * back to the left. At each call, you know there are no conflicts with
	 * leftCandidate from the left. You look for a conflict on the right (or
	 * failing that, the next non-conflict on the right) and recurse on that to
	 * get the next labeled Perspective to the right. You draw leftCandidate iff
	 * it doesn't conflict with that next label.
	 */
	Perspective drawComputedLabel(Perspective leftCandidate, double divisor) {
		assert query().isQueryValid();
		Perspective result = null;
		int x1 = -1;
		int x0 = -1;
		int threshold = -1;
		if (leftCandidate != null) {
			x0 = midLabelPixel(leftCandidate, divisor);
			threshold = priorityCount(leftCandidate);
			x1 = x0 + labelHprojectionW;
		}
		int frontW = (int) getWidth();
		// Util.print("drawComputedLabel " + p + "." + leftCandidate + " " + x0
		// + " " + threshold);
		for (int x = x0 + 1; x < frontW && result == null; x++) {
			if (x > x1)
				threshold = -1;
			Perspective rightCandidate = labelXs[x];
			if (rightCandidate != null
					&& priorityCount(rightCandidate) > threshold) {
				Perspective nextDrawn = drawComputedLabel(rightCandidate,
						divisor);
				if (nextDrawn != null
						&& midLabelPixel(nextDrawn, divisor) <= midLabelPixel(
								rightCandidate, divisor)
								+ labelHprojectionW) {
					result = nextDrawn;
				} else {
					result = rightCandidate;
					maybeDrawLabel(result);
				}
			}
		}
		return result;
	}
}

// This goes on rankLabel
// final class ShowPerspectiveDocHandler extends MyInputEventHandler {
//
// public ShowPerspectiveDocHandler() {
// super(PText.class);
// }
//
// PerspectiveViz getPerspectiveViz(LazyPNode node) {
// if (node == null || node instanceof PerspectiveViz)
// return (PerspectiveViz) node;
// return getPerspectiveViz(node.getParent());
// }
//
// Perspective getFacet(LazyPNode node) {
// if (node instanceof FacetNode)
// return ((FacetNode) node).getFacet();
// else if (node == null)
// return null;
// else
// return getFacet(node.getParent());
// }
//
// public boolean enter(PNode node) {
// Util.print("ShowPerspectiveDocHandler.enter");
// Perspective facet = getFacet(node);
// PerspectiveViz pv = getPerspectiveViz(node);
// if (pv != null)
// pv.showDoc(facet, true);
// return pv != null;
// }
//
// public boolean exit(PNode node) {
// PerspectiveViz pv = getPerspectiveViz(node);
// if (pv != null)
// pv.showDoc(null, false);
// return pv != null;
// }
// }

final class HotZoneListener extends PBasicInputEventHandler {

	PerspectiveViz pv;

	HotZoneListener(PerspectiveViz _pv) {
		pv = _pv;
	}

	public void mouseEntered(PInputEvent e) {
		double y = e.getPositionRelativeTo(e.getPickedNode()).getY();
		pv.hotZone(y);
		e.setHandled(true);
	}

	public void mouseExited(PInputEvent e) {
		pv.loseHotZone();
		e.setHandled(true);
	}

	public void mouseMoved(PInputEvent e) {
		double y = e.getPositionRelativeTo(e.getPickedNode()).getY();
		pv.hotZone(y);
		e.setHandled(true);
	}

}

final class SqueezablePNode extends LazyPPath {

	// PBounds clip;

	double goalYscale = -1;

	private double goalY = -1;

	// private long loadTime;

	SqueezablePNode() {
		setBounds(0, 0, 1, 1);
		setPickable(false);
	}

	void setVisible() {
		// PerspectiveViz pv = ((PerspectiveViz) getParent());
		setVisible(goalYscale > 0);
	}

	void layout(double y, double yScale) {
		// Util.print("PV.layout " + getParent() + " scale = " + yScale + " "
		// + goalYscale + " " + y + " " + goalY);
		// PerspectiveViz pv = ((PerspectiveViz) getParent());
		if (yScale != goalYscale || y != goalY) {
			goalYscale = yScale;
			goalY = y;
			boolean isVisible = (yScale > 0);
			// Util.print("PV.layout scale = " + yScale + " " + y);
			setVisible(isVisible);
			if (isVisible) {
				setTransform(Util.scaleNtranslate(1.0, yScale, 0.0, y));
			}
		}
	}

	public void setVisible(boolean visible) {
		super.setVisible(visible);
		setChildrenPickable(visible);
	}

	// protected void paint(PPaintContext paintContext) {
	// Graphics2D g2 = paintContext.getGraphics();
	// g2.setPaint(Bungee.summaryFG);
	// g2.setStroke(LazyPPath.getStrokeInstance(0));
	// g2.draw(getBounds());
	// }

	// protected void paint(PPaintContext paintContext) {
	// // loadTime = new Date().getTime();
	// if (clip != null) {
	// // Util.print("SqueezablePNode.paint " + clip.getY() + " " +
	// // getVisible());
	// paintContext.pushClip(clip);
	// super.paint(paintContext);
	// paintContext.popClip(clip);
	// } else
	// super.paint(paintContext);
	// }

	// void paintAfterChildren(PPaintContext paintContext) {
	// if (clip != null)
	// paintContext.popClip(clip);
	//
	// // Util.print("painting " + ((PerspectiveViz) getParent()).p + " took "
	// // + (new Date().getTime() - loadTime));
	// }
}
