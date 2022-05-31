package com.baselet.element.draw;

import java.util.Arrays;
import java.util.List;

import com.baselet.control.basics.geom.PointDouble;
import com.baselet.diagram.draw.DrawHandler;

public class DrawHelper {

	public static List<PointDouble> drawPackage(DrawHandler drawer, double upperLeftX, double upperLeftY, double titleHeight, double titleWidth, double fullHeight, double fullWidth) {
		PointDouble start = new PointDouble(upperLeftX, upperLeftY);
		List<PointDouble> points = Arrays.asList(
				start,
				new PointDouble(upperLeftX + titleWidth, upperLeftY),
				new PointDouble(upperLeftX + titleWidth, upperLeftY + titleHeight),
				new PointDouble(upperLeftX + fullWidth, upperLeftY + titleHeight),
				new PointDouble(upperLeftX + fullWidth, upperLeftY + fullHeight),
				new PointDouble(upperLeftX, upperLeftY + fullHeight),
				start);
		drawer.drawLines(points);
		drawer.drawLines(new PointDouble(upperLeftX, upperLeftY + titleHeight), new PointDouble(upperLeftX + titleWidth, upperLeftY + titleHeight));
		return points;
	}

	public static void drawActor(DrawHandler drawer, int hCenter, int yTop, double dimension) {
		drawer.drawCircle(hCenter, yTop + DrawHelper.headRadius(dimension), DrawHelper.headRadius(dimension)); // Head
		drawer.drawLine(hCenter - DrawHelper.armLength(dimension), yTop + DrawHelper.armHeight(dimension), hCenter + DrawHelper.armLength(dimension), yTop + DrawHelper.armHeight(dimension)); // Arms
		drawer.drawLine(hCenter, yTop + DrawHelper.headRadius(dimension) * 2, hCenter, yTop + DrawHelper.headToBodyLength(dimension)); // Body
		drawer.drawLine(hCenter, yTop + DrawHelper.headToBodyLength(dimension), hCenter - DrawHelper.legSpan(dimension), yTop + DrawHelper.headToLegLength(dimension)); // Legs
		drawer.drawLine(hCenter, yTop + DrawHelper.headToBodyLength(dimension), hCenter + DrawHelper.legSpan(dimension), yTop + DrawHelper.headToLegLength(dimension)); // Legs
	}

	public static double headToLegLength(double dimension) {
		return legSpan(dimension) * 2 + headToBodyLength(dimension);
	}

	private static double legSpan(double dimension) {
		return dimension;
	}

	private static double headToBodyLength(double dimension) {
		return dimension * 2 + headRadius(dimension) * 2;
	}

	private static double armHeight(double dimension) {
		return armLength(dimension);
	}

	public static double armLength(double dimension) {
		return dimension * 1.5;
	}

	private static double headRadius(double dimension) {
		return dimension / 2;
	}


	public static void drawEntity(DrawHandler drawer, int hCenter, int yTop, double dimension) {
		drawer.drawCircle(hCenter, yTop + DrawHelper.entityCircleRadius(dimension), DrawHelper.entityCircleRadius(dimension)); // Circle
		drawer.drawLine(hCenter - DrawHelper.entityCircleRadius(dimension), yTop + DrawHelper.entityCircleRadius(dimension) * 2, hCenter + DrawHelper.entityCircleRadius(dimension), yTop + DrawHelper.entityCircleRadius(dimension) * 2); // BottomLine
	}

	public static double entityCircleRadius(double dimension) {
		return dimension * 1.5;
	}

	public static void drawBoundary(DrawHandler drawer, int hCenter, int yTop, double dimension) {
		drawer.drawCircle(hCenter, yTop + DrawHelper.boundaryCircleRadius(dimension), DrawHelper.boundaryCircleRadius(dimension)); // Circle
		drawer.drawLine(hCenter - DrawHelper.boundaryCircleRadius(dimension) - middleLineLength(dimension) + middleLineLeftPadding(dimension), yTop, hCenter - DrawHelper.boundaryCircleRadius(dimension) - middleLineLength(dimension) + middleLineLeftPadding(dimension), yTop + DrawHelper.boundaryCircleRadius(dimension) * 2); // LeftVerticalLine
		drawer.drawLine(hCenter - DrawHelper.boundaryCircleRadius(dimension) - middleLineLength(dimension) + middleLineLeftPadding(dimension), yTop + DrawHelper.boundaryCircleRadius(dimension), hCenter - DrawHelper.boundaryCircleRadius(dimension), yTop + DrawHelper.boundaryCircleRadius(dimension)); // MiddleLine
	}

	public static double boundaryCircleRadius(double dimension) {
		return dimension * 1.5;
	}

	public static double middleLineLength(double dimension) {
		return dimension;
	}

	private static double middleLineLeftPadding(double dimension) {
		return dimension / 2;
	}

	public static void drawControl(DrawHandler drawer, int hCenter, int yTop, double dimension) {
		drawer.drawCircle(hCenter, yTop + DrawHelper.arrowSpan(dimension) + DrawHelper.controlCircleRadius(dimension), DrawHelper.controlCircleRadius(dimension)); // Circle
		drawer.drawLine(hCenter + DrawHelper.arrowSpan(dimension), yTop, hCenter, yTop + DrawHelper.arrowSpan(dimension)); // ArrowUpperLine
		drawer.drawLine(hCenter, yTop + DrawHelper.arrowSpan(dimension), hCenter + DrawHelper.arrowSpan(dimension), yTop + DrawHelper.arrowSpan(dimension) * 2); // ArrowLowerLine
	}

	public static double controlCircleRadius(double dimension) {
		return dimension * 1.5;
	}

	public static double arrowSpan(double dimension) {
		return dimension / 2;
	}
}
