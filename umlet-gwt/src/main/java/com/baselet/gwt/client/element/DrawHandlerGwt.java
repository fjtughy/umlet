package com.baselet.gwt.client.element;

import com.baselet.control.StringStyle;
import com.baselet.control.basics.geom.DimensionDouble;
import com.baselet.control.basics.geom.PointDouble;
import com.baselet.control.constants.SharedConstants;
import com.baselet.control.enums.AlignHorizontal;
import com.baselet.control.enums.FormatLabels;
import com.baselet.control.enums.LineType;
import com.baselet.diagram.draw.DrawFunction;
import com.baselet.diagram.draw.DrawHandler;
import com.baselet.diagram.draw.helper.ColorOwn;
import com.baselet.diagram.draw.helper.Style;
import com.baselet.gwt.client.base.Converter;
import com.baselet.gwt.client.base.Notification;
import com.baselet.gwt.client.view.Context2dGwtWrapper;
import com.baselet.gwt.client.view.Context2dPdfWrapper;
import com.baselet.gwt.client.view.Context2dWrapper;
import com.baselet.gwt.client.logging.CustomLogger;
import com.baselet.gwt.client.logging.CustomLoggerFactory;
import com.baselet.gwt.client.text.Font;
import com.google.gwt.canvas.dom.client.Context2d.TextAlign;

public class DrawHandlerGwt extends DrawHandler {

	private static final CustomLogger log = CustomLoggerFactory.getLogger(DrawHandlerGwt.class);

	private final Context2dWrapper ctx;

	public DrawHandlerGwt(Context2dWrapper context2d) {
		this(context2d, 1.0d);
	}

	private double scalingFactor;
	private boolean scalingIsSet;

	private double zoomFactor = SharedConstants.DEFAULT_GRID_SIZE / 10.0;

	public DrawHandlerGwt(Context2dWrapper context2d, double scaling) {
		ctx = context2d;
		scalingFactor = scaling;
		scalingIsSet = false;
		javascriptCodeParser = new JavascriptParserGwt(this);
	}

	public void setNewScaling(double scalingFactor) {

		this.scalingFactor = scalingFactor;
		scalingIsSet = false;
	}

	private void setScalingOnce() {
		if (!scalingIsSet) {
			ctx.setTransform(1, 0, 0, 1, 0, 0);
			ctx.scale(scalingFactor, scalingFactor);
			scalingIsSet = true;
		}
	}

	@Override
	protected DimensionDouble textDimensionHelper(StringStyle singleLine) {
		Font oldFont = ctx.getFont();
		ctxSetFont(style.getFontSize(), singleLine);
		DimensionDouble dim = new DimensionDouble(ctx.measureText(singleLine.getStringWithoutMarkup()), style.getFontSize()); // unfortunately a html canvas offers no method to get the exakt height, therefore just use the fontsize
		ctx.setFont(oldFont); // restore old font to make sure the textDimensions method doesnt change context state!
		return dim;
	}

	@Override
	protected double getDefaultFontSize() {
		return 12;
	}

	@Override
	public void drawArc(final double x, final double y, final double width, final double height, final double start, final double extent, final boolean open) {
		final Style styleAtDrawingCall = style.cloneFromMe();
		addDrawable(new DrawFunction() {
			@Override
			public void run() {
				setScalingOnce();
				setStyle(ctx, styleAtDrawingCall);

				double centerX = (int) (x * zoomFactor + width * zoomFactor / 2) + HALF_PX;
				double centerY = (int) (y * zoomFactor + height * zoomFactor / 2) + HALF_PX;

				ctx.save();
				// translate the arc and don't use the center parameters because they are affected by scaling
				ctx.translate(centerX, centerY);
				ctx.scale(1, (height * zoomFactor) / (width * zoomFactor));
				if (ctx instanceof Context2dGwtWrapper) { // PDF resets sub-paths on moveTo, therefore we need to draw closed arcs ourselves
					if (open) { // if arc should be open, move before the path begins
						ctx.beginPath();
					}
					else { // otherwise the move is part of the path
						ctx.beginPath();
						ctx.moveTo(0, 0);
					}
					ctx.arc(0, 0, width * zoomFactor / 2, -Math.toRadians(start), -Math.toRadians(start + extent), true);
					if (!open) { // close path only if arc is not open and not PDF
						ctx.closePath();
					}
					// restore before drawing so the line has the same with and is not affected by the scaling
					ctx.restore();
					fill(ctx, styleAtDrawingCall.getLineWidth() > 0);
				}
				else {
					if (open) {
						ctx.arc(0, 0, width * zoomFactor / 2, -Math.toRadians(start), -Math.toRadians(start + extent), true);
					}
					else {
						ctx.moveTo(0, 0);
						ctx.arc(0, 0, width * zoomFactor / 2, -Math.toRadians(start), -(Math.toRadians(start + extent)), true);
						ctx.lineTo(0, 0);
						ctx.closePath();
					}
					// TODO: Find a way to draw lines with uniform line width
					fill(ctx, styleAtDrawingCall.getLineWidth() > 0);
					ctx.restore();
				}
			}
		});
	}

	@Override
	public void drawCircle(final double x, final double y, final double radius) {
		final Style styleAtDrawingCall = style.cloneFromMe();
		addDrawable(new DrawFunction() {
			@Override
			public void run() {
				setScalingOnce();
				setStyle(ctx, styleAtDrawingCall);
				ctx.beginPath();
				ctx.arc((int) x * zoomFactor + HALF_PX, (int) y * zoomFactor + HALF_PX, radius * zoomFactor, 0, 2 * Math.PI);
				fill(ctx, styleAtDrawingCall.getLineWidth() > 0);
			}
		});
	}

	@Override
	public void drawEllipse(final double x, final double y, final double width, final double height) {
		final Style styleAtDrawingCall = style.cloneFromMe();
		addDrawable(new DrawFunction() {
			@Override
			public void run() {
				setScalingOnce();
				setStyle(ctx, styleAtDrawingCall);
				drawEllipseHelper(ctx, styleAtDrawingCall.getLineWidth() > 0, (int) x * zoomFactor + HALF_PX, (int) y * zoomFactor + HALF_PX, width * zoomFactor, height * zoomFactor);
			}
		});
	}

	@Override
	public void drawLines(final PointDouble... points) {
		if (points.length > 1) {
			final Style styleAtDrawingCall = style.cloneFromMe();
			addDrawable(new DrawFunction() {
				@Override
				public void run() {
					PointDouble[] pointsCopy = new PointDouble[points.length];
					for (int i = 0; i < points.length; i++) {
						PointDouble point = new PointDouble(points[i].getX() * zoomFactor, points[i].getY() * zoomFactor);
						pointsCopy[i] = point;
					}
					setScalingOnce();
					setStyle(ctx, styleAtDrawingCall);
					drawLineHelper(styleAtDrawingCall.getLineWidth() > 0, pointsCopy);
				}
			});
		}
	}

	@Override
	public void drawRectangle(final double x, final double y, final double width, final double height) {
		final Style styleAtDrawingCall = style.cloneFromMe();
		addDrawable(new DrawFunction() {
			@Override
			public void run() {
				setScalingOnce();
				setStyle(ctx, styleAtDrawingCall);
				ctx.beginPath();
				ctx.rect((int) x * zoomFactor + HALF_PX, (int) y * zoomFactor + HALF_PX, (int) (width * zoomFactor), (int) (height * zoomFactor));
				fill(ctx, styleAtDrawingCall.getLineWidth() > 0);
			}
		});
	}

	@Override
	public void drawRectangleRound(final double x, final double y, final double width, final double height, final double radius) {
		final Style styleAtDrawingCall = style.cloneFromMe();
		addDrawable(new DrawFunction() {
			@Override
			public void run() {
				setScalingOnce();
				setStyle(ctx, styleAtDrawingCall);
				drawRoundRectHelper(ctx, styleAtDrawingCall.getLineWidth() > 0, (int) x * zoomFactor + HALF_PX, (int) y * zoomFactor + HALF_PX, (int) width * zoomFactor, (int) height * zoomFactor, radius * zoomFactor);
			}
		});
	}

	@Override
	public void drawBase64Image(final double x, final double y, final double width, final double height, final String imageString) {
		addDrawable(new DrawFunction() {
			@Override
			public void run() {
				drawBase64ImageHelper(ctx, (int) x * zoomFactor, (int) y * zoomFactor, (int) width * zoomFactor, (int) height * zoomFactor, imageString);
			}
		});
	}

	@Override
	public void printHelper(final StringStyle[] text, final PointDouble point, final AlignHorizontal align) {
		final Style styleAtDrawingCall = style.cloneFromMe();
		addDrawable(new DrawFunction() {
			@Override
			public void run() {
				setScalingOnce();
				PointDouble pToDraw = new PointDouble(point.getX() * zoomFactor, point.getY() * zoomFactor);
				ColorOwn fgColor = getOverlay().getForegroundColor() != null ? getOverlay().getForegroundColor() : styleAtDrawingCall.getForegroundColor();
				ctx.setFillStyle(Converter.convert(fgColor));
				for (StringStyle line : text) {
					drawTextHelper(line, pToDraw, align, styleAtDrawingCall.getFontSize() * zoomFactor);
					pToDraw = new PointDouble(pToDraw.getX(), pToDraw.getY() + textHeightMax());
				}
			}
		});
	}

	private void drawTextHelper(final StringStyle line, PointDouble p, AlignHorizontal align, double fontSize) {

		ctxSetFont(fontSize, line);

		String textToDraw = line.getStringWithoutMarkup();
		if (textToDraw == null || textToDraw.isEmpty()) {
			return; // if nothing should be drawn return (some browsers like Opera have problems with ctx.fillText calls on empty strings)
		}

		if (ctx instanceof Context2dPdfWrapper) {
			// Replacing tabulator unicode because some fonts don't include it
			textToDraw = textToDraw.replaceAll("\u0009", " ");
		}

		ctxSetTextAlign(align);
		ctx.fillText(textToDraw, p.x, p.y);

		if (line.getFormat().contains(FormatLabels.UNDERLINE)) {
			ctx.setLineWidth(1.0f);
			setLineDash(ctx, LineType.SOLID, 1.0f);
			double textWidth = textWidth(line) * zoomFactor;
			int vDist = 1;
			switch (align) {
				case LEFT:
					drawLineHelper(true, new PointDouble(p.x, p.y + vDist), new PointDouble(p.x + textWidth, p.y + vDist));
					break;
				case CENTER:
					drawLineHelper(true, new PointDouble(p.x - textWidth / 2, p.y + vDist), new PointDouble(p.x + textWidth / 2, p.y + vDist));
					break;
				case RIGHT:
					drawLineHelper(true, new PointDouble(p.x - textWidth, p.y + vDist), new PointDouble(p.x, p.y + vDist));
					break;
			}
		}
	}

	private void ctxSetFont(double fontSize, StringStyle stringStyle) {
		ctx.setFont(fontSize, stringStyle);
	}

	private void ctxSetTextAlign(AlignHorizontal align) {
		TextAlign ctxAlign = null;
		switch (align) {
			case LEFT:
				ctxAlign = TextAlign.LEFT;
				break;
			case CENTER:
				ctxAlign = TextAlign.CENTER;
				break;
			case RIGHT:
				ctxAlign = TextAlign.RIGHT;
				break;
		}
		ctx.setTextAlign(ctxAlign);
	}

	/**
	 * based on http://stackoverflow.com/questions/2172798/how-to-draw-an-oval-in-html5-canvas/2173084#2173084
	 */
	private static void drawEllipseHelper(Context2dWrapper ctx, boolean drawOuterLine, double x, double y, double w, double h) {
		double kappa = .5522848f;
		double ox = w / 2 * kappa; // control point offset horizontal
		double oy = h / 2 * kappa; // control point offset vertical
		double xe = x + w; // x-end
		double ye = y + h; // y-end
		double xm = x + w / 2; // x-middle
		double ym = y + h / 2; // y-middle

		ctx.beginPath();
		ctx.moveTo(x, ym);
		ctx.bezierCurveTo(x, ym - oy, xm - ox, y, xm, y);
		ctx.bezierCurveTo(xm + ox, y, xe, ym - oy, xe, ym);
		ctx.bezierCurveTo(xe, ym + oy, xm + ox, ye, xm, ye);
		ctx.bezierCurveTo(xm - ox, ye, x, ym + oy, x, ym);

		fill(ctx, drawOuterLine);
	}

	/**
	 * based on http://js-bits.blogspot.co.at/2010/07/canvas-rounded-corner-rectangles.html
	 */
	private static void drawRoundRectHelper(Context2dWrapper ctx, boolean drawOuterLine, final double x, final double y, final double width, final double height, final double radius) {
		ctx.beginPath();
		ctx.moveTo(x + radius, y);
		ctx.lineTo(x + width - radius, y);
		ctx.quadraticCurveTo(x + width, y, x + width, y + radius);
		ctx.lineTo(x + width, y + height - radius);
		ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
		ctx.lineTo(x + radius, y + height);
		ctx.quadraticCurveTo(x, y + height, x, y + height - radius);
		ctx.lineTo(x, y + radius);
		ctx.quadraticCurveTo(x, y, x + radius, y);
		ctx.closePath();
		fill(ctx, drawOuterLine);
	}

	private static void drawBase64ImageHelper(Context2dWrapper ctx, final double x, final double y, final double width, final double height, final String imageString) {
		ctx.drawBase64Image(imageString, x, y, width, height);
	}

	private void drawLineHelper(boolean drawOuterLine, PointDouble... points) {
		ctx.beginPath();
		boolean first = true;
		for (PointDouble point : points) {
			if (first) {
				ctx.moveTo(point.x.intValue() + HALF_PX, point.y.intValue() + HALF_PX); // +0.5 because a line of thickness 1.0 spans 50% left and 50% right (therefore it would not be on the 1 pixel - see https://developer.mozilla.org/en-US/docs/HTML/Canvas/Tutorial/Applying_styles_and_colors)
				first = false;
			}
			ctx.lineTo(point.x.intValue() + HALF_PX, point.y.intValue() + HALF_PX);
		}
		if (points[0].equals(points[points.length - 1])) {
			fill(ctx, drawOuterLine); // only fill if first point == lastpoint
		}
		if (drawOuterLine) {
			ctx.stroke();
		}
	}

	private void setStyle(Context2dWrapper ctx, Style style) {
		if (style.getBackgroundColor() != null) {
			ctx.setFillStyle(Converter.convert(style.getBackgroundColor()));
		}
		ColorOwn fgColor = getOverlay().getForegroundColor() != null ? getOverlay().getForegroundColor() : style.getForegroundColor();
		if (fgColor != null) {
			ctx.setStrokeStyle(Converter.convert(fgColor));
		}
		ctx.setLineWidth(style.getLineWidth());
		setLineDash(ctx, style.getLineType(), style.getLineWidth());
	}

	private void setLineDash(Context2dWrapper ctx, LineType lineType, double lineThickness) {
		try {
			double dotPatternLength = Math.max(2, lineThickness);
			double dashPatternLength = 6 * Math.max(1, lineThickness / 2);
			switch (lineType) {
				case DASHED: // large linethickness values need longer dashes
					ctx.setLineDash(dashPatternLength);
					break;
				case DOTTED: // minimum must be 2, otherwise the dotting is not really visible
					ctx.setLineDash(dotPatternLength);
					break;
				case DOT_DASHED:
					ctx.setLineDash(dotPatternLength, dashPatternLength);
					break;
				case DOT_DOT_DASHED:
					ctx.setLineDash(dotPatternLength, dotPatternLength, dashPatternLength);
					break;
				default: // default is a solid line
					ctx.setLineDash(0);
			}
		} catch (Exception e) {
			log.debug("No browser support for dashed lines", e);
			Notification.showFeatureNotSupported("Dashed and dotted lines are shown as solid lines<br/>To correctly display them, please use Firefox or Chrome", true);
		}
	}

	public Context2dWrapper getCtx() {
		return ctx;
	}

	// The PDF spec does not allow consecutive calls to fill and stroke
	private static void fill(Context2dWrapper ctx, boolean stroke) {
		if (ctx instanceof Context2dPdfWrapper) {
			if (stroke) {
				((Context2dPdfWrapper) ctx).fillAndStroke();
			}
			else {
				ctx.fill();
			}
		}
		else {
			ctx.fill();
			if (stroke) {
				ctx.stroke();
			}
		}
	}

	public double getZoomFactor() {
		return zoomFactor;
	}

	public void setZoomFactor(double zoomFactor) {
		this.zoomFactor = zoomFactor;
	}
}
