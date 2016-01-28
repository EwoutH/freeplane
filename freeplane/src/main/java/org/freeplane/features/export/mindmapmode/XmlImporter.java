package org.freeplane.features.export.mindmapmode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import javax.swing.JOptionPane;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.FileUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.MapWriter.Mode;
import org.freeplane.features.map.mindmapmode.MMapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.url.mindmapmode.MFileManager;
import org.freeplane.n3.nanoxml.XMLException;
import org.freeplane.n3.nanoxml.XMLParseException;

public class XmlImporter	{

	final private String xsltResource;
	public XmlImporter(final String xsltResource){
		this.xsltResource = xsltResource;

	}

	public void importXml(final File file) throws XMLParseException, MalformedURLException, IOException, URISyntaxException, XMLException{
		FileInputStream in = null;
		try{
			in = new FileInputStream(file);
			final File directory = file.getParentFile();
			final File outputFile = new File (directory, file.getName() + org.freeplane.features.url.UrlManager.FREEPLANE_FILE_EXTENSION);
			importXml(in, outputFile);
		}
		finally {
			FileUtils.silentlyClose(in);
		}
	}

	public void importXml(final InputStream in, final File outputFile) throws IOException, FileNotFoundException,
	XMLParseException, URISyntaxException, XMLException, MalformedURLException {
		final URL xsltUrl = ResourceController.getResourceController().getResource(xsltResource);
		if (xsltUrl == null) {
			LogUtils.severe("Can't find " + xsltResource + " as resource.");
			throw new IllegalArgumentException("Can't find " + xsltResource + " as resource.");
		}
		final URL mapUrl = Compat.fileToUrl(outputFile);
		if(outputFile.exists()){
			if(Controller.getCurrentController().getMapViewManager().tryToChangeToMapView(mapUrl))
				return;
			final int overwriteMap = JOptionPane.showConfirmDialog(Controller.getCurrentController()
					.getMapViewManager().getMapViewComponent(), TextUtils.getText("map_already_exists"), "Freeplane",
					JOptionPane.YES_NO_OPTION);
			if (overwriteMap != JOptionPane.YES_OPTION) {
				return ;
			}
		}
		final PipedReader reader = new PipedReader();
		final Writer writer = new PipedWriter(reader);
		final Thread transformationThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				InputStream xsltFile = null;
				try{
					xsltFile = xsltUrl.openStream();
					final Result result = new StreamResult(writer);
					transform(new StreamSource(in), xsltFile, result);
				} catch (IOException e) {
					e.printStackTrace();
				}
				finally {
					FileUtils.silentlyClose(xsltFile);
					FileUtils.silentlyClose(writer);
				}
			}
		}, "XSLT Transformation");
		transformationThread.start();
		final ModeController modeController = Controller.getCurrentModeController();
		final MapController mapController = modeController.getMapController();
		final MapModel map = new MMapModel();
		modeController.getMapController().getMapReader().createNodeTreeFromXml(map, reader, Mode.FILE);
		map.setURL(mapUrl);
		map.setSaved(false);
		mapController.fireMapCreated(map);
		mapController.newMapView(map);
	}

	private void transform(final Source xmlSource, final InputStream xsltStream, final Result result)
			throws TransformerFactoryConfigurationError {
		final Source xsltSource = new StreamSource(xsltStream);
		try {
			final TransformerFactory transFact = TransformerFactory.newInstance();
			final Transformer trans = transFact.newTransformer(xsltSource);
			trans.transform(xmlSource, result);
		}
		catch (final Exception e) {
			LogUtils.severe(e);
		}
	}
}