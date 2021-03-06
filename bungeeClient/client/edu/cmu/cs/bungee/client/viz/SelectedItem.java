/* 

 Created on Mar 4, 2005

 Bungee View lets you search, browse, and data-mine an image collection.  
 Copyright (C) 2006  Mark Derthick

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.  See gpl.html.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 You may also contact the author at 
 mad@cs.cmu.edu, 
 or at
 Mark Derthick
 Carnegie-Mellon University
 Human-Computer Interaction Institute
 Pittsburgh, PA 15213

 */

package edu.cmu.cs.bungee.client.viz;

import java.awt.Color;
import java.awt.Font;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.cmu.cs.bungee.client.query.DisplayTree;
import edu.cmu.cs.bungee.client.query.FacetTree;
import edu.cmu.cs.bungee.client.query.ItemPredicate;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.Query;
import edu.cmu.cs.bungee.client.query.Query.Item;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.threads.UpdateThread;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.Boundary;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyContainer;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPPath;
import edu.cmu.cs.bungee.piccoloUtils.gui.MouseDoc;
import edu.cmu.cs.bungee.piccoloUtils.gui.MyInputEventHandler;
import edu.cmu.cs.bungee.piccoloUtils.gui.TextBox;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.nodes.PImage;

final class SelectedItem extends LazyContainer implements MouseDoc {

	private static final long serialVersionUID = 1L;

	private static final boolean CAPTURING_FACES = false;

	private static final boolean FLIPPING_FACES = false;

	private final static double leftMargin = 5.0;

	double minTextBoxH;

	FieldedTextBox selectedItemSummaryTextBox = null;

	transient SoftReference<Map<Item, DisplayTree>> facetTrees;

	Bungee art;

	Item currentItem;

	private APText label;

	private LazyPPath outline;

	FacetTreeViz facetTreeViz;

	ItemSetter setter;

	private Boundary boundary;

	static final ItemClickHandler itemClickHandler = new ItemClickHandler();

	SelectedItem(Bungee a) {
		art = a;

		label = art.oneLineLabel();
		label.scale(2.0);
		label.setTextPaint(Bungee.selectedItemFG);
		label.setPickable(false);
		// labelH = 2 * art.lineH;
		label.setText("Selected " + art.query.getGenericObjectLabel(false));

		// separatorW = art.getStringWidth(" > ");
		setPickable(false);
		// setPaint(Bungee.selectedItemBG);
	}

	void flip() {
		File dir = new File(
				"C:\\Documents and Settings\\mad\\Desktop\\50thAnniversary\\FlipImages");
		ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
		int bits[] = new int[] { 8 };
		ColorModel cm = new ComponentColorModel(cs, bits, false, false,
				Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
		for (int j = 0; j < 100; j++) {
			try {
				assert dir.isDirectory() : dir;
				String[] children = dir.list();
				PNode prev = null;
				if (children != null) {
					if (j == 0)
						Util.print("Flipping " + children.length + " images");
					for (int i = 0; i < children.length; i++) {
						if (children[i].endsWith(".jpg")
								&& children[i].length() <= 6) {
							File f = new File(dir, children[i]);
							if (j == 0)
								Util.print("Fliping " + f);
							BufferedImage rawImage = Util
									.read(new FileInputStream(f));
							BufferedImage image = Util.convertImage(rawImage,
									cm);
							PImage pim = new PImage(image);
							addChild(pim);
							// pim.moveToBack();
							if (prev != null)
								prev.removeFromParent();
							prev = pim;
							art.getCanvas().paintImmediately();
							pim.animateToTransparency(0, 1000);
							Thread.sleep(100);
							harvestPims();
						}
					}
				}
				Thread.sleep(1000);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@SuppressWarnings("unchecked")
	void harvestPims() {
		for (Iterator<PNode> it = getChildrenIterator(); it.hasNext();) {
			PNode child = it.next();
			if (child.getTransparency() == 0)
				removeChild(child);
		}
	}

	void init() {
		// initSeparators();
		setter = new ItemSetter(this);
		setter.start();

		// setPaint(Art.selectedItemBG);

		outline = new LazyPPath();
		outline.setVisible(false);
		outline.setStrokePaint(Bungee.selectedItemOutlineColor);
		outline.setPickable(false);
		outline.setStroke(LazyPPath.getStrokeInstance(1));
		outline.setPathToRectangle(0, 0, 1, 1);
		// outline.setPaint(null);
		addChild(outline);

		boundary = new Boundary(this, true);

		facetTreeViz = new FacetTreeViz(art);
		// facetTree.setOffset(leftMargin, 0);
		addChild(facetTreeViz);
		validate(w, h);
	}

	private boolean isInitted() {
		return outline != null;
	}

	void validate(double _w, double _h) {
		// Util.print("SI.validate " + isInitted() + " " + w);
		assert _w >= minWidth() : _w;
		w = _w;
		h = _h;
		minTextBoxH = _h / 5;
		// setBounds(0, 0, w, _h);
		if (isInitted()) {
			outline.setBounds(0, 0, w - 1, _h);
			// if (selectionTreeScrollBar != null)
			// removeChild(selectionTreeScrollBar);
			label.setFont(art.font);
			// label.setHeight(art.lineH);
			label.setOffset(Math.round((w - labelWidth()) / 2.0), 0.0);

			facetTreeViz.validate(getTreeW(), _h);
			boundary.validate();
			redraw();
			if (FLIPPING_FACES)
				flip();
		}
	}

	double getTreeW() {
		return w - 2 * leftMargin;
	}

	@Override
	public void updateBoundary(Boundary boundary1) {
		assert boundary1 == boundary;
		if (art.getShowBoundaries() && selectedItemSummaryTextBox != null) {
			minTextBoxH = boundary1.center()
					- selectedItemSummaryTextBox.getYOffset();
			redraw();
		}
	}

	@Override
	public void enterBoundary(Boundary boundary1) {
		if (!art.getShowBoundaries()) {
			boundary1.exit();
		}
	}

	double labelWidth() {
		// return art.getStringWidth(label.getText()) * label.getScale();
		return label.getWidth() * label.getScale();
	}

	@Override
	public double minWidth() {
		// assert Util.ignore(boundary1);
		// Util.print("SI.minWidth fontsize=" + getFontSize());
		return labelWidth();
	}

	int getFontSize() {
		return label.getFont().getSize();
	}

	@Override
	public double minHeight() {
		// assert boundary1 == boundary;
		double minH = art.lineH * 3;
		if (selectedItemSummaryTextBox != null) {
			minH += selectedItemSummaryTextBox.getYOffset();
		}
		return minH;
	}

	@Override
	public double maxHeight() {
		// assert boundary1 == boundary;
		return facetTreeViz.getYOffset() + facetTreeViz.getHeight() - art.lineH
				* 4;
	}

	void animateOutline(Item item, double x, double y, double rectW,
			double rectH) {
		if (currentItem != item) {
			// Util.print("SelectedItem.animateOutline " + x + " " + y + " "
			// + item);
			if (y >= 0) {
				outline.moveToFront();
				outline.setVisible(true);
				outline.setBounds(x, y, rectW, rectH);
				outline.animateToBounds(0, 0, w - 1, h, 500);
			}
			currentItem = item;
			// Util.print("animateOutline " + currentItem );
			if (getFacetTree() == null) {
				setter.set(item);
				removeTree();
			} else {
				facetTreeViz.setTree(getFacetTree());
				redraw();
			}
		}
	}

	void removeTree() {
		removeAllChildren();
		addChild(outline);
		// if (selectedItemSummaryTextBox != null) {
		// removeChild(selectedItemSummaryTextBox);
		selectedItemSummaryTextBox = null;
		// }
		// if (gridImage != null) {
		// removeChild(gridImage);
		gridImage = null;
		// }
		// if (facetTreeViz != null)
		// facetTreeViz.removeTree();
	}

	private transient Runnable doRedraw;

	Runnable getDoRedraw() {
		if (doRedraw == null)
			doRedraw = new Runnable() {

				public void run() {
					facetTreeViz.setTree(getFacetTree());
					redraw();
				}
			};
		return doRedraw;
	}

	private GridElementWrapper gridImage;

	private Set<String> prevSearches = new HashSet<String>();

	// private IntHashtable foo;

	int maxImageW() {
		return (int) (w - art.lineH);
	}

	int maxImageH() {
		int result = (int) (h / 2.0);
		// Util.print("maxImageH " + result);
		return result;
	}

	double maybeAddImage() {
		// int imageH = 0;
		int y = (int) (label.getMaxY() + 0 * art.lineH / 2);
		FacetTree ft = getFacetTree();
		if (ft != null) {
			// ItemImage ii = ft.image;
			// imageH = ii.currentH();
			// int imageW = ii.currentW();
			// double maxW = maxImageW();
			// double maxH = maxImageH();
			// double scale = Math.min(1, Math.min(maxW / imageW, maxH /
			// imageH));
			// imageW *= scale;
			// imageH *= scale;
			Item item = (Item) ft.treeObject();
			ItemImage ii = art.lookupItemImage(item);
			if (ii == null) {
				setter.set(item);
			} else {
				gridImage = new GridElementWrapper(ii, false);
				// Util.print("SI.maybeAddImage calling scale");
				gridImage.setSize(maxImageW(), maxImageH());
				// gridImage.mouseDoc = query().itemURLdoc;
				gridImage.pNode().removeInputEventListener(
						GridElementWrapper.gridElementHandler);
				gridImage.pNode().addInputEventListener(itemClickHandler);

				if (CAPTURING_FACES)
					gridImage.pNode().setScale(
							maxImageW() / gridImage.getWidth());

				int x = (int) Math.round((w - gridImage.getWidth()
						* gridImage.getScale()) / 2.0);
				// image.setBounds(x, y, imageW, imageH);
				gridImage.setOffset(x, y);
				y += gridImage.getHeight() * gridImage.getScale() + art.lineH
						/ 2;
				// Util.print("ImageH " + gridImage.getHeight() + " * "
				// + gridImage.getScale() + " = "
				// + (gridImage.getHeight() * gridImage.getScale()));
				addChild(gridImage.pNode());
			}
		}
		return y;
	}

	boolean hasFacet(Perspective facet) {
		// Util.print("hasFacet " + gridImage.itemImage.facets);
		// If there is no selected item (so gridImage is null) middle menu
		// should not offer to add this facet.
		return gridImage == null || facet.getOnCount() < 0
				|| gridImage.itemImage != null
				&& gridImage.itemImage.facets.contains(facet);
	}

	boolean lacksFacet(Perspective facet) {
		return gridImage == null || facet.getOnCount() >= 0
				&& gridImage.itemImage != null
				&& !gridImage.itemImage.facets.contains(facet);
	}

	boolean isHidden() {
		return selectedItemSummaryTextBox == null;
	}

	void setVisibility() {
		boolean hide = query().getOnCount() == 0;
		// Util.print("Selected.hide " + state + " " + (state != isHidden()));
		if (hide != isHidden()) {
			outline.setVisible(!hide);
			if (hide)
				removeTree();
			else {
				redraw();
				if (label.getParent() == null) {
					addChild(label);
					moveAheadOf(art.grid); // Make outline animation
					// visible.
					// setPaint(Art.selectedItemBG);
				}
			}
		}
	}

	private Map<Item, DisplayTree> getFacetTreeTable() {
		return (facetTrees == null ? null : facetTrees.get());
	}

	FacetTree getFacetTree() {
		Map<Item, DisplayTree> table = getFacetTreeTable();
		return (FacetTree) (table == null ? null : table.get(currentItem));
	}

	/**
	 * Called in thread itemSetter
	 */
	void addFacetTree(Item item, DisplayTree tree) {
		Map<Item, DisplayTree> table = getFacetTreeTable();
		if (table == null) {
			// Util.print("new facetTrees");
			table = new Hashtable<Item, DisplayTree>();
			facetTrees = new SoftReference<Map<Item, DisplayTree>>(table);
			// foo = table;
		}
		table.put(item, tree);
	}

	void removeFacetTree(Item item) {
		// Util.print("removeFacetTree " + item);
		Map<Item, DisplayTree> table = facetTrees.get();
		if (table != null) {
			table.remove(item);
		}
	}

	// y margins:
	// <top> 0 <label> 10 <image> 10 <desc> 10 <tree> 2 <bottom>
	// x margins:
	// <left> leftMargin <textBox> leftMargin <right>
	// <left> leftMargin <facetTree> leftMargin <scrollbar> leftMargin <right>
	void redraw() {
		// Util.print("SelectedItem.redraw " + currentItem + " "
		// + (getFacetTree() != null));
		removeTree();
		if (currentItem != null) {
			FacetTree facetTree = getFacetTree();
			if (facetTree != null) {
				// can be null if editing decached it

				addChild(label);
				addChild(facetTreeViz);
				double descY = Math.ceil(maybeAddImage());
				// if (true)return;
				// double descY = labelH + imageH + 20.0;
				double usableW = w - leftMargin * 2.0;
				double usableH = h - descY - 12.0; // for desc &
				// tree

				facetTreeViz.validate(getTreeW(), usableH);
				int nSelectedItemTreeMinLines = facetTreeViz.drawTree();
				// Util.print("SI.redraw " + nSelectedItemTreeMinLines);
				if (nSelectedItemTreeMinLines >= 0) {
					double maxDescH = Math.max(minTextBoxH, usableH
							- nSelectedItemTreeMinLines * art.lineH);

					selectedItemSummaryTextBox = new FieldedTextBox(usableW,
							maxDescH, facetTree.description(),
							Bungee.textScrollBG, Bungee.textScrollFG,
							Bungee.selectedItemFG, art.lineH, art.font, query()
									.itemDescriptionFields());
					if (art.getIsEditing())
						selectedItemSummaryTextBox.setEditable(true, art
								.getCanvas(), getEdit());
					// selectedItemSummaryTextBox.startEditing(art.getCanvas());
					selectedItemSummaryTextBox.setOffset(leftMargin, descY);
					addChild(selectedItemSummaryTextBox);
					double treeY = descY
							+ selectedItemSummaryTextBox.getHeight() + 10.0;
					double availableH = h - treeY - 2.0;
					facetTreeViz.setOffset(leftMargin, treeY);
					facetTreeViz.validate(getTreeW(), availableH);
					boundary.setCenter(treeY);
					int nInvisibleLines = facetTreeViz
							.redraw(nSelectedItemTreeMinLines);

					if (nInvisibleLines > 0
							|| selectedItemSummaryTextBox.isScrollBar()) {
						if (boundary.getParent() == null) {
							addChild(boundary);
						}
					} else {
						if (boundary.getParent() != null) {
							removeChild(boundary);
						}
					}
				}
			} else {
				setter.set(currentItem);
			}
			prevSearches = new HashSet<String>();
			updateColors();
		}
	}

	class FieldedTextBox extends TextBox {
		private static final long serialVersionUID = 1L;
		final String[] fields;
		String rawText;

		public FieldedTextBox(double w, double max, String _s, Color Scroll_BG,
				Color Scroll_FG, Color _color, double lineH, Font font,
				String[] fields) {
			super(w, max, null, Scroll_BG, Scroll_FG, _color, lineH, font);
			assert fields.length > 0;
			this.fields = fields;

			// Have to do this after setting fields
			setText(_s);
		}

		/*
		 * return the displayable text
		 */
		@Override
		public String getText() {
			String displayText = null;
			if (rawText != null) {
				String[] values = rawText.split("\\n\\d+\\n");
				StringBuffer buf = new StringBuffer();
				Pattern p = Pattern.compile("\\n(\\d+)\\n");
				Matcher m = p.matcher(rawText);
				int valueIndex = 1;
				while (m.find()) {
					if (valueIndex > 1) {
						buf.append("\n \n");
					}
					int fieldIndex = Integer.parseInt(m.group(1));
					assert fieldIndex >= 0 && fieldIndex < fields.length;
					String value = values[valueIndex++];
					if (isEditing()) {
						buf.append("<").append(fields[fieldIndex])
								.append(">\n");
					}
					buf.append(value);
				}
				assert valueIndex == values.length;
				displayText = buf.toString();
			}
			return displayText;
		}

		@Override
		public void setText(String rawText) {
			this.rawText = rawText;
			super.setText(getText());
		}

		public String getRawText() {
			return super.getText();
		}

	}

	boolean highlight(String substring) {
		return selectedItemSummaryTextBox != null
				&& selectedItemSummaryTextBox.highlight(substring);
	}

	<V extends ItemPredicate>void highlightFacet(Set<V> facets) {
		if (facetTreeViz != null)
			facetTreeViz.highlightFacet(facets);
	}

	void updateColors() {
		if (facetTreeViz != null)
			facetTreeViz.updateColors();
		Set<String> searches = query().getSearches();
		if (!searches.equals(prevSearches)) {
			prevSearches = new HashSet<String>(searches);
			highlight(null);
			for (String s: searches) {
				highlight(s);
			}
		}
	}

	// void clickText(Perspective facet, int modifiers) {
	// if (facetTreeViz != null)
	// facetTreeViz.clickText(facet, modifiers);
	// }

	// public void setMouseDoc(PNode source, boolean state) {
	// art.setMouseDoc(source, state);
	// }

	public void setMouseDoc(String doc) {
		art.setMouseDoc(doc);
	}

	// public void setMouseDoc(Markup doc, boolean state) {
	// art.setMouseDoc(doc, state);
	// }

	void stop() {
		if (setter != null) {
			setter.exit();
			setter = null;
		}
	}

	void itemMenu(boolean isRight) {
		art.itemMiddleMenu(currentItem, isRight);
	}

	void showItemInNewWindow() {
		art.showItemInNewWindow(currentItem);
	}

	/**
	 * Called when mouse enters/exits the thumbnail image.
	 * 
	 * @param state
	 *            true means enter.
	 */
	void highlight(boolean state) {
		String desc = null;
		if (state)
			desc = query().itemURLdoc;
		if (desc != null && desc.length() == 0)
			desc = null;
		art.setClickDesc(desc);
	}

	/**
	 * Only called by replayOp
	 * 
	 * @param i
	 */
	void clickImage(Item i) {
		if (i == currentItem)
			showItemInNewWindow();
	}

	private transient Runnable edit;

	Runnable getEdit() {
		if (edit == null)
			edit = new Runnable() {

				public void run() {
					String description = selectedItemSummaryTextBox
							.getRawText();
					art.setItemDescription(currentItem, description);
				}
			};
		return edit;
	}

	void doHideTransients() {
		if (selectedItemSummaryTextBox != null)
			selectedItemSummaryTextBox.revert();
	}

	Query query() {
		return art.query;
	}
}

final class ItemSetter extends UpdateThread {

	private SelectedItem parent;

	void set(Item item) {
		add(item);
	}

	ItemSetter(SelectedItem _parent) {
		super("ItemSetter", -2);
		parent = _parent;
	}

	@Override
	public void process(Object i) {
		// Util.print("ItemSetter.run");
		Bungee art = parent.art;
		Query query = art.query;
		Item item = (Item) i;

		int imageW = parent.maxImageW();
		int imageH = parent.maxImageH();
		ItemImage _image = art.lookupItemImage(item);
		if (_image != null
				&& _image.bigEnough(imageW, imageH, Bungee.IMAGE_QUALITY)) {
			// -1 means don't retrieve an image
			imageW = -1;
			imageH = -1;
		}
		// Util.print("ItemSetter.process " + i + " " + imageW + "x" + imageH);

		ResultSet rs = null;
		String description = null;
		try {
			rs = query.getDescAndImage(item, imageW, imageH,
					Bungee.IMAGE_QUALITY);
			while (rs.next()) {
				description = rs.getString(1);
				InputStream blobStream = ((MyResultSet) rs).getInputStream(2);
				art.ensureItemImage(item, rs.getInt(3), rs.getInt(4),
						Bungee.IMAGE_QUALITY, blobStream, description);
				if (art.getIsEditing()
						&& (description == null || description.length() == 0))
					description = "click to add a description";
			}
		} catch (Throwable e) {
			e.printStackTrace();
		} finally {
			query.close(rs);
		}

		DisplayTree tree = new FacetTree(item, description, query);
		parent.addFacetTree(item, tree);
		if (isUpToDate()) {
			javax.swing.SwingUtilities.invokeLater(parent.getDoRedraw());
		}
	}
}

final class ItemClickHandler extends MyInputEventHandler {

	ItemClickHandler() {
		super(SelectedItem.class);
	}

	// SelectedItem getSelectedItem(PNode node) {
	// PNode maybeSelectedItem = node.getParent();
	// if (maybeSelectedItem instanceof GridImage)
	// maybeSelectedItem = maybeSelectedItem.getParent();
	// return (SelectedItem) maybeSelectedItem;
	// }

	// GridImage getImage(PNode node) {
	// PNode maybeSelectedItem = node;
	// if (!(maybeSelectedItem instanceof GridImage))
	// maybeSelectedItem = maybeSelectedItem.getParent();
	// return (GridImage) maybeSelectedItem;
	// }

	@Override
	public boolean enter(PNode node) {
		// SelectedItem parent = getSelectedItem(node);
		((SelectedItem) node).highlight(true);
		return true;
	}

	@Override
	public boolean exit(PNode node) {
		// SelectedItem parent = getSelectedItem(node);
		// if (parent != null)
		((SelectedItem) node).highlight(false);
		return true;
	}

	@Override
	public boolean click(PNode node, PInputEvent e) {
		SelectedItem parent = ((SelectedItem) node);
		// Util.print("SI.click "+e+" "+e.isMiddleMouseButton());
		if (e.isControlDown()) {
			// getImage(node).handleFaceWarping(e);
		} else if (e.isMiddleMouseButton())
			parent.itemMenu(false);
		else if (e.isRightMouseButton())
			parent.itemMenu(true);
		else
			parent.showItemInNewWindow();
		return true;
	}
}
