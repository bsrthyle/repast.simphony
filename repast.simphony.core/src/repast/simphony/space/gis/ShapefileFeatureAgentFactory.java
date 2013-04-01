package repast.simphony.space.gis;

import java.beans.IntrospectionException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.feature.SchemaException;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import simphony.util.messages.MessageCenter;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

/**
 * Factory class for creating feature agents that
 * are appropriate for writing to shapfiles. Such features
 * have only primitive or String attributes. Instances of the this class
 * can be created using the FeatureAgentFactoryFinder.
 *
 */
public class ShapefileFeatureAgentFactory<T> extends FeatureAgentFactory {
	MessageCenter msg = MessageCenter.getMessageCenter(getClass());

	private CoordinateReferenceSystem crs = DefaultGeographicCRS.WGS84;

	private Class<? extends Geometry> geometryType = Point.class;

	private SimpleFeatureType featureType;

	private List<SimpleFeature> features;
  private List<FeatureAttributeAdapter> adapters;

  ShapefileFeatureAgentFactory(Class<T> agentType, Class<? extends Geometry> geometryType,
                                    CoordinateReferenceSystem crs, List<FeatureAttributeAdapter> adapters) {
    this.geometryType = geometryType;
    this.crs = crs;
    this.adapters = adapters;
    init(agentType, adapters);
	}

  /**
   * Gets the created feature type.
   *
   * @return the created feature type.
   */
  public SimpleFeatureType getFeatureType() {
    return featureType;
  }

  /**
   * Gets the coordinate reference system for this factory.
   *
   * @return the coordinate reference system for this factory.
   */
  public CoordinateReferenceSystem getCrs() {
    return crs;
  }

  /**
   * Resets this factory by creating a new feature collection.
   */
  public void reset() {
  	 features = new ArrayList<SimpleFeature>();
  }

  private void init(Class<T> agentType, List<FeatureAttributeAdapter> adapters) {
		try {
			featureType = getShapefileFeatureType(agentType, crs, geometryType, adapters);
			features = new ArrayList<SimpleFeature>();
		} catch (IntrospectionException e) {
			msg.error("Unable to introspect feature class: "
					+ agentType.getName(), e);
		} catch (SchemaException e) {
			msg.error("Error creating FeatureType", e);
		}
	}

	public FeatureAgent getFeature(T agent, Geography geography) {
		FeatureAgent<T> featureAgent = new FeatureAgent<T>(featureType, agent, geography, adapters);
		features.add(featureAgent);
		return featureAgent;
	}

	public List<SimpleFeature> getFeatures() {
		return features;
	}
}