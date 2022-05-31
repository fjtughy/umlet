package com.baselet.generator;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baselet.control.basics.geom.Rectangle;
import com.baselet.control.enums.ElementId;
import com.baselet.diagram.CurrentDiagram;
import com.baselet.diagram.DiagramHandler;
import com.baselet.diagram.Notifier;
import com.baselet.element.ElementFactorySwing;
import com.baselet.element.interfaces.GridElement;
import com.baselet.generator.java.Field;
import com.baselet.generator.java.JavaClass;
import com.baselet.generator.java.Method;
import com.baselet.generator.sorting.AlphabetLayout;
import com.baselet.generator.sorting.SortableElement;
import com.baselet.gui.command.AddElement;

/**
 * Creates a base64Image element from a filename pointing to an image file,
 * adds the image to the current diagram and resizes this element to minimum size where all image is visible.
 */
public class Base64ImageDiagramConverter {

	private final Logger log = LoggerFactory.getLogger(Base64ImageDiagramConverter.class);

	public Base64ImageDiagramConverter() {
	}

	public static String convertFailuresToString(List<Exception> failures) {
		StringBuilder sb = new StringBuilder();
		for (Exception failure : failures) {
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append(failure.getMessage());
		}
		return sb.toString();
	}

	public void createBase64ImageDiagrams(List<String> filesToOpen) {
		List<Exception> failures = new ArrayList<Exception>();
		List<SortableElement> elements = new ArrayList<SortableElement>();
		for (String filename : filesToOpen) {
			try {
				SortableElement element = createElement(filename);
				if (element != null) {
					elements.add(element);
				}
			} catch (Exception e) {
				failures.add(e);
			}
		}

		if (!failures.isEmpty()) {
			Notifier.getInstance().showError(ClassDiagramConverter.convertFailuresToString(failures));
			return; // if errors are in any of the files don't add any of them
		}

		new AlphabetLayout().layout(elements);

		addElementsToDiagram(elements);
	}

	private SortableElement createElement(final String filename) throws Exception {
		JavaClass parsedClass = new JavaClass() {
		
			public String getName() {
				return filename;
			}
		
			public Field[] getFields() {
				return new Field[0];
			}
		
			public Method[] getMethods() {
				return new Method[0];
			}
		
			public ClassRole getRole() {
				return ClassRole.CLASS;
			}
		
			public String getPackage() {
				return "base64image";
			}
		}
		;
		String propertiesText = getElementProperties(filename);
		List<String> propList = Arrays.asList(propertiesText.split("\n"));
		Rectangle initialSize = new Rectangle(0, 0, 260, 120); //TODO
		GridElement clazz = ElementFactorySwing.create(ElementId.UMLClass, initialSize, propertiesText, null, CurrentDiagram.getInstance().getDiagramHandler());
		return new SortableElement(clazz, parsedClass);
	}

	private void addElementsToDiagram(List<SortableElement> elements) {
		DiagramHandler handler = CurrentDiagram.getInstance().getDiagramHandler();

		for (SortableElement e : elements) {
			new AddElement(e.getElement(),
					handler.realignToGrid(e.getElement().getRectangle().x),
					handler.realignToGrid(e.getElement().getRectangle().y), false).execute(handler);
		}
		handler.setChanged(true);
	}

	private String getElementProperties(String filename) throws Exception {
		StringBuilder sb = new StringBuilder("_umletcode=\n");
		sb.append("drawBase64Image(0,0,width,height,\"");

		byte[] fileContent = Files.readAllBytes(Paths.get(filename));
		sb.append(Base64.getEncoder().encodeToString(fileContent));

		sb.append("\")");

		return sb.toString();
	}
}

