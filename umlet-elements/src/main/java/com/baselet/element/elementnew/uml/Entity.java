package com.baselet.element.elementnew.uml;

import com.baselet.control.SharedUtils;
import com.baselet.control.basics.geom.Rectangle;
import com.baselet.control.enums.ElementId;
import com.baselet.control.enums.ElementStyle;
import com.baselet.diagram.draw.DrawHandler;
import com.baselet.element.NewGridElement;
import com.baselet.element.draw.DrawHelper;
import com.baselet.element.facet.PropertiesParserState;
import com.baselet.element.facet.Settings;
import com.baselet.element.settings.SettingsManualResizeTop;
import com.baselet.element.sticking.StickingPolygon;
import com.baselet.element.sticking.polygon.StickingPolygonGenerator;

public class Entity extends NewGridElement {

	private final static double ENTITY_DIMENSION = 10;

	private final StickingPolygonGenerator entityStickingPolygonGenerator = new StickingPolygonGenerator() {
		@Override
		public StickingPolygon generateStickingBorder(Rectangle rect) {
			double dimension = ENTITY_DIMENSION;
			double hCenter = getRealSize().width / 2.0;
			double vCenter = getRealSize().height / 2.0;
			int left = SharedUtils.realignToGrid(false, hCenter - DrawHelper.entityCircleRadius(dimension), false);
			int right = SharedUtils.realignToGrid(false, hCenter + DrawHelper.entityCircleRadius(dimension), true);
			int top = SharedUtils.realignToGrid(false, vCenter - DrawHelper.entityCircleRadius(dimension), false);
			int bottom = SharedUtils.realignToGrid(false, vCenter + DrawHelper.entityCircleRadius(dimension), true);
			int height = bottom - top;

			StickingPolygon p = new StickingPolygon(rect.x, rect.y);
			p.addPoint(left, 0);
			p.addPoint(right, 0);
			p.addPoint(right, height);
			p.addPoint(left, height, true);
			return p;
		}
	};

	@Override
	protected Settings createSettings() {
		return new SettingsManualResizeTop(){
			@Override
			public ElementStyle getElementStyle() {
				return ElementStyle.AUTORESIZE;
			}
		};
	}

	@Override
	public ElementId getId() {
		return ElementId.UMLEntity;
	}

	@Override
	protected void drawCommonContent(PropertiesParserState state) {
		DrawHandler drawer = state.getDrawer();
		double dimension = ENTITY_DIMENSION;
		state.updateMinimumSize(DrawHelper.armLength(dimension) * 2, DrawHelper.headToLegLength(dimension));

		DrawHelper.drawEntity(drawer, getRealSize().width / 2, 0, dimension);

		state.setStickingPolygonGenerator(entityStickingPolygonGenerator);
	}
}
