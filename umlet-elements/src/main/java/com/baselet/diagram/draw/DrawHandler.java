package com.baselet.diagram.draw;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import com.baselet.control.StringStyle;
import com.baselet.control.basics.geom.DimensionDouble;
import com.baselet.control.basics.geom.Line;
import com.baselet.control.basics.geom.Lines;
import com.baselet.control.basics.geom.PointDouble;
import com.baselet.control.basics.geom.Rectangle;
import com.baselet.control.constants.FacetConstants;
import com.baselet.control.enums.AlignHorizontal;
import com.baselet.control.enums.FormatLabels;
import com.baselet.control.enums.LineType;
import com.baselet.diagram.draw.helper.ColorOwn;
import com.baselet.diagram.draw.helper.ColorOwn.Transparency;
import com.baselet.diagram.draw.helper.Style;
import com.baselet.diagram.draw.helper.StyleException;
import com.baselet.diagram.draw.helper.theme.Theme;
import com.baselet.diagram.draw.helper.theme.ThemeFactory;

public abstract class DrawHandler {

	protected static final double HALF_PX = 0.5f;

	public JavascriptCodeParser javascriptCodeParser;

	protected Style style = new Style();
	private final Style overlay = new Style();

	private final ArrayList<DrawFunction> drawablesBackground = new ArrayList<DrawFunction>();
	private final ArrayList<DrawFunction> drawablesForeground = new ArrayList<DrawFunction>();

	public static enum Layer {
		Foreground, Background
	}

	private Layer layer = Layer.Background;

	private boolean enableDrawing = true;

	/**
	 * all background elements are drawn before drawing the foreground elements
	 * can be useful e.g. if a printText call is made before a drawRectangle call although it should be placed behind the rectangle
	 */
	public void setLayer(Layer layer) {
		this.layer = layer;
	}

	public void setEnableDrawing(boolean enableDrawing) {
		this.enableDrawing = enableDrawing;
	}

	protected Style getOverlay() {
		return overlay;
	}

	protected void addDrawable(DrawFunction drawable) {
		if (enableDrawing) {
			if (layer == Layer.Foreground) {
				drawablesForeground.add(drawable);
			}
			else {
				drawablesBackground.add(drawable);
			}
		}
		// if drawing is disabled don't add the DrawFunction to any collection
	}

	public void drawAll(boolean isSelected) {
		if (isSelected) {
			overlay.setForegroundColor(ThemeFactory.getCurrentTheme().getColor(Theme.ColorStyle.SELECTION_FG));
		}
		else {
			overlay.setForegroundColor(null);
		}
		drawAll();
	}

	public void clearCache() {
		drawablesBackground.clear();
		drawablesForeground.clear();
	}

	public final double textHeightMaxWithSpace() {
		return textHeightMax() + getDistanceBetweenTextLines();
	}

	public final double textHeightMax() {
		return textDimension(new StringStyle(Collections.<FormatLabels> emptySet(), "Hy")).getHeight(); // "Hy" is a good dummy for a generic max height and depth
	}

	/**
	 * @param singleLineWithMarkup a single line (no \n) with interpreted markup
	 * @return the height of the given text
	 * @see StringStyle#replaceNotEscaped(String)
	 * @see StringStyle#analyzeFormatLabels(String)
	 * @see #textHeight(StringStyle)
	 */
	public final double textHeight(String singleLineWithMarkup) {
		return textHeight(escapeAndAnalyzeSingleLine(singleLineWithMarkup));
	}

	/**
	 * @param singleLine a single line (no \n), no further processing of the String takes place.
	 * i.e. The text is printed unmodified with the given formating.
	 * @return the height of the given text
	 */
	public final double textHeight(StringStyle singleLine) {
		return textDimension(singleLine).getHeight();
	}

	/**
	 *
	 * @param singleLineWithMarkup a single line (no \n) with interpreted markup
	 * @return the width of the given text
	 * @see StringStyle#replaceNotEscaped(String)
	 * @see StringStyle#analyzeFormatLabels(String)
	 * @see #textWidth(StringStyle)
	 */
	public final double textWidth(String singleLineWithMarkup) {
		return textWidth(escapeAndAnalyzeSingleLine(singleLineWithMarkup));
	}

	/**
	 * @param singleLine single line (no \n), no further processing of the String takes place.
	 * i.e. The text is printed unmodified with the given formating.
	 * @return the width of the given text
	 */
	public final double textWidth(StringStyle singleLine) {
		return textDimension(singleLine).getWidth();
	}

	public final void setForegroundColor(String color) {
		if (color.equals(FacetConstants.FOREGROUND_COLOR_KEY)) {
			setForegroundColor(ThemeFactory.getCurrentTheme().getColor(Theme.ColorStyle.DEFAULT_FOREGROUND));
		}
		else {
			setForegroundColor(ThemeFactory.getCurrentTheme().forString(color, Transparency.FOREGROUND)); // if fgColor is not a valid string null will be set
		}
	}

	public final void setForegroundColor(ColorOwn color) {
		if (color == null) {
			style.setForegroundColor(ThemeFactory.getCurrentTheme().getColor(Theme.ColorStyle.DEFAULT_FOREGROUND));
		}
		else {
			style.setForegroundColor(color);
		}
	}

	public final void setBackgroundColorAndKeepTransparency(String color) {
		if (color.equals(FacetConstants.BACKGROUND_COLOR_KEY)) {
			setBackgroundColor(ThemeFactory.getCurrentTheme().getColor(Theme.ColorStyle.DEFAULT_BACKGROUND));
		}
		else {
			// #295: if bg is the default, use background transparency, but if bg has been set reuse its transparency (otherwise transparency= would only work if the line comes after bg=)
			Theme currentTheme = ThemeFactory.getCurrentTheme();
			ColorOwn oldBg = getBackgroundColor();
			ColorOwn defaultBg = currentTheme.getColor(Theme.ColorStyle.DEFAULT_BACKGROUND);
			int newAlpha = oldBg == defaultBg ? Transparency.BACKGROUND.getAlpha() : oldBg.getAlpha();
			setBackgroundColor(currentTheme.forString(color, newAlpha));
		}
	}

	public final void setBackgroundColor(ColorOwn color) {
		if (color == null) {
			style.setBackgroundColor(ThemeFactory.getCurrentTheme().getColor(Theme.ColorStyle.DEFAULT_BACKGROUND));
		}
		else {
			style.setBackgroundColor(color);
		}
	}

	public final void setTransparency(double transparencyVal) {
		if (transparencyVal < 0 || transparencyVal > 100) {
			throw new StyleException("The transparency value must be between 0 and 100");
		}
		double colorTransparencyValue = 255 - transparencyVal * 2.55; /* ColorOwn has 0 for full transparency and 255 for no transparency */
		ColorOwn bgColor = getBackgroundColor();
		setBackgroundColor(bgColor.transparency((int) colorTransparencyValue));
	}

	public ColorOwn getForegroundColor() {
		return style.getForegroundColor();
	}

	public ColorOwn getBackgroundColor() {
		return style.getBackgroundColor();
	}

	public double getLineWidth() {
		return style.getLineWidth();
	}

	public void resetColorSettings() {
		setForegroundColor(FacetConstants.FOREGROUND_COLOR_KEY);
		setBackgroundColorAndKeepTransparency(FacetConstants.BACKGROUND_COLOR_KEY);
	}

	public final void setFontSize(double fontSize) {
		assertDoubleRange(fontSize);
		style.setFontSize(fontSize);
	}

	public double getFontSize() {
		return style.getFontSize();
	}

	public final void setLineType(LineType type) {
		style.setLineType(type);
	}

	public final void setLineType(String lineTypeString) {
		LineType lineType = LineType.fromString(lineTypeString);
		style.setLineType(lineType);
	}

	public LineType getLineType() {
		return style.getLineType();
	}

	public final void setLineWidth(double lineWidth) {
		assertDoubleRange(lineWidth);
		style.setLineWidth(lineWidth);
	}

	private void assertDoubleRange(double doubleValue) {
		if (doubleValue < 0 || doubleValue > 5000) {
			throw new StyleException("value must be >=0 and <=5000");
		}
	}

	public void resetStyle() {
		resetColorSettings();
		style.setFontSize(getDefaultFontSize());
		style.setLineType(LineType.SOLID);
		style.setLineWidth(1);
	}

	public Style getStyleClone() {
		return style.cloneFromMe();
	}

	public void setStyle(Style style) {
		this.style = style.cloneFromMe();
	}

	public void drawAll() {
		for (DrawFunction d : drawablesBackground) {
			d.run();
		}
		for (DrawFunction d : drawablesForeground) {
			d.run();
		}
	}

	public double getDistanceBorderToText() {
		return 5;
	}

	public double getDistanceBetweenTextLines() {
		return 3;
	}

	/**
	 * @param singleLine single line (no \n), no further processing of the String takes place.
	 * i.e. The text is printed unmodified with the given formating.
	 * @return the dimension the text would need if printed with the current settings
	 */
	protected DimensionDouble textDimension(StringStyle singleLine) {
		return textDimensionHelper(singleLine);
	}

	/* HELPER METHODS */

	/**
	 * @param singleLine single line (no \n), no further processing of the String takes place.
	 * i.e. The text is printed unmodified with the given formating.
	 * @return the dimension the text would need if printed with the current settings
	 */
	protected abstract DimensionDouble textDimensionHelper(StringStyle singleLine);

	protected abstract double getDefaultFontSize();

	/* DRAW METHODS */
	public void drawRectangle(Rectangle rect) {
		drawRectangle(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
	}

	public void drawLine(Line line) {
		drawLine(line.getStart().getX(), line.getStart().getY(), line.getEnd().getX(), line.getEnd().getY());
	}

	public void drawLine(double x1, double y1, double x2, double y2) {
		drawLines(new PointDouble(x1, y1), new PointDouble(x2, y2));
	}

	public void drawLines(Collection<PointDouble> points) {
		drawLines(points.toArray(new PointDouble[points.size()]));
	}

	public void drawLines(Line... lines) {
		drawLines(Lines.toPoints(lines));
	}

	protected StringStyle escapeAndAnalyzeSingleLine(String singleLineWithMarkup) {
		return StringStyle.analyzeFormatLabels(StringStyle.replaceNotEscaped(singleLineWithMarkup));
	}

	public void print(String multiLineWithMarkup, double x, double y, AlignHorizontal align) {
		print(multiLineWithMarkup, new PointDouble(x, y), align);
	}

	public void print(StringStyle singleLine, double x, double y, AlignHorizontal align) {
		printHelper(new StringStyle[] { singleLine }, new PointDouble(x, y), align);
	}

	/**
	 * @param multiLineWithMarkup can contain multiple lines (separated by \n). Each line is then analyzed and printed.
	 * @param point
	 * @param align the horizontal alignment
	 */
	public void print(String multiLineWithMarkup, PointDouble point, AlignHorizontal align) {
		String[] lines = multiLineWithMarkup.split("\n");
		StringStyle[] formatedLines = new StringStyle[lines.length];
		for (int i = 0; i < lines.length; i++) {
			formatedLines[i] = escapeAndAnalyzeSingleLine(lines[i]);
		}
		printHelper(formatedLines, point, align);
	}

	/**
	 * @param lines each element is a single line (no \n), no further processing of the String takes place.
	 * i.e. The each element is printed unmodified with the given formating.
	 * @param format the format which is used for each line
	 * @param point
	 * @param align the horizontal alignment
	 */
	public void print(String[] lines, Set<FormatLabels> format, PointDouble point, AlignHorizontal align) {
		StringStyle[] formatedLines = new StringStyle[lines.length];
		for (int i = 0; i < lines.length; i++) {
			formatedLines[i] = new StringStyle(format, lines[i]);
		}
		printHelper(formatedLines, point, align);
	}

	/**
	 * @param x topLeft corner of the surrounding rectangle of the ellipse
	 * @param y topLeft corner of the surrounding rectangle of the ellipse
	 * @param width of the full ellipse, i.e. width of the surrounding rectangle of the ellipse
	 * @param height of the full ellipse, i.e. height of the surrounding rectangle of the ellipse
	 * @param start of the arc in degrees. 0 corresponds to the right side of a horizontal line. 90 corresponds to the top of a vertical line.
	 * @param extent can be up to 360 (extend in degrees from the start parameter)
	 */
	public abstract void drawArc(double x, double y, double width, double height, double start, double extent, boolean open);

	public void drawArc(final double x, final double y, final double width, final double height, final double start, final double extent, final boolean open,
			String bgColor, String fgColor, String lineTypeString, Double lineWidth, Double transparency) {
		setExtraDrawValues(bgColor, fgColor, lineTypeString, lineWidth, transparency);
		drawArc(x, y, width, height, start, extent, open);
	}

	public abstract void drawCircle(double x, double y, double radius);

	public void drawCircle(double x, double y, double radius, String bgColor, String fgColor, String lineTypeString, Double lineWidth, Double transparency) {
		setExtraDrawValues(bgColor, fgColor, lineTypeString, lineWidth, transparency);
		drawCircle(x, y, radius);
	}

	public abstract void drawEllipse(double x, double y, double width, double height);

	public void drawEllipse(final double x, final double y, final double width, final double height, String bgColor, String fgColor, String lineTypeString, Double lineWidth, Double transparency) {
		setExtraDrawValues(bgColor, fgColor, lineTypeString, lineWidth, transparency);
		drawEllipse(x, y, width, height);
	}

	public abstract void drawLines(PointDouble... points);

	public abstract void drawRectangle(double x, double y, double width, double height);

	public void drawRectangle(final double x, final double y, final double width, final double height, String bgColor, String fgColor, String lineTypeString, Double lineWidth, Double transparency) {
		setExtraDrawValues(bgColor, fgColor, lineTypeString, lineWidth, transparency);
		drawRectangle(x, y, width, height);
	}

	public abstract void drawRectangleRound(double x, double y, double width, double height, double radius);

	public void drawRectangleRound(final double x, final double y, final double width, final double height, final double radius, String bgColor, String fgColor, String lineTypeString, Double lineWidth, Double transparency) {
		setExtraDrawValues(bgColor, fgColor, lineTypeString, lineWidth, transparency);
		drawRectangleRound(x, y, width, height, radius);
	}

	public void drawLine(double x1, double y1, double x2, double y2, String fgColor, String lineTypeString, Double lineWidth) {
		if (lineWidth != null) {
			setLineWidth(lineWidth);
		}
		if (lineTypeString != null) {
			setLineType(lineTypeString);
		}
		if (fgColor != null) {
			setForegroundColor(fgColor);
		}
		drawLine(x1, y1, x2, y2);
	}

	public abstract void drawBase64Image(double x, double y, double width, double height, String imageString);

	public void print(String multiLineWithMarkup, double x, double y, AlignHorizontal align, String fgColor) {
		if (fgColor != null) {
			setForegroundColor(fgColor);
		}
		print(multiLineWithMarkup, x, y, align);
	}

	private void setExtraDrawValues(String bgColor, String fgColor, String lineTypeString, Double lineWidth, Double transparency) {
		if (lineWidth != null) {
			setLineWidth(lineWidth);
		}
		if (lineTypeString != null) {
			setLineType(lineTypeString);
		}
		if (fgColor != null) {
			setForegroundColor(fgColor);
		}
		if (bgColor != null) {
			setBackgroundColorAndKeepTransparency(bgColor);
		}
		if (transparency != null) {
			setTransparency(transparency);
		}
	}

	/**
	 * @param lines each element is a single line (no \n), no further processing of the String takes place.
	 * i.e. The each element is printed unmodified with the given formating.
	 * @param point
	 * @param align the horizontal alignment
	 */
	public abstract void printHelper(StringStyle[] lines, PointDouble point, AlignHorizontal align);

	public JavascriptCodeParser getJavascriptCodeParser() {
		return javascriptCodeParser;
	}

	public void setJavascriptCodeParser(JavascriptCodeParser javascriptCodeParser) {
		this.javascriptCodeParser = javascriptCodeParser;
	}

}