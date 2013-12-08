package org.tdmx.console.application.search;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tdmx.console.application.domain.DomainObject;
import org.tdmx.console.application.domain.DomainObjectChangesHolder;
import org.tdmx.console.application.domain.ServiceProviderDO;
import org.tdmx.console.application.domain.visit.Traversal;
import org.tdmx.console.application.domain.visit.TraversalContextHolder;
import org.tdmx.console.application.domain.visit.TraversalFunction;
import org.tdmx.console.application.search.FieldDescriptor.DomainObjectType;
import org.tdmx.console.application.search.FieldDescriptor.FieldType;
import org.tdmx.console.application.service.ObjectRegistry;
import org.tdmx.console.application.service.SystemProxyServiceImpl;


/**
 * 
 * @author Peter
 *
 */
public class SearchServiceImpl implements SearchService {

	//-------------------------------------------------------------------------
	//PUBLIC CONSTANTS
	//-------------------------------------------------------------------------

	//-------------------------------------------------------------------------
	//PROTECTED AND PRIVATE VARIABLES AND CONSTANTS
	//-------------------------------------------------------------------------
	private Logger log = LoggerFactory.getLogger(SystemProxyServiceImpl.class);

	private static List<FieldDescriptor> allDescriptors = new ArrayList<>();
	//TODO registry
	
	//TODO DsnResolverList
	
	//TODO SystemProxyList
	
	//TODO RootCAList
	
	private static final class ServiceProviderSO {
		public static final FieldDescriptor SUBJECT		 	= new FieldDescriptor(DomainObjectType.ServiceProvider, "subject", FieldType.Text);
		public static final FieldDescriptor MAS_HOSTNAME 	= new FieldDescriptor(DomainObjectType.ServiceProvider, "mas.hostname", FieldType.String);
		public static final FieldDescriptor MAS_PORT 		= new FieldDescriptor(DomainObjectType.ServiceProvider, "mas.port", FieldType.Number);
		public static final FieldDescriptor MAS_PROXY 		= new FieldDescriptor(DomainObjectType.ServiceProvider, "mas.proxy", FieldType.String);
	}
	
	static {
		allDescriptors.add(ServiceProviderSO.SUBJECT);
		allDescriptors.add(ServiceProviderSO.MAS_HOSTNAME);
		allDescriptors.add(ServiceProviderSO.MAS_PORT);
		allDescriptors.add(ServiceProviderSO.MAS_PROXY);
	}
	
	private ObjectRegistry objectRegistry;
	private SearchContext searchContext = new SearchContext();
	
	//-------------------------------------------------------------------------
	//CONSTRUCTORS
	//-------------------------------------------------------------------------

	//-------------------------------------------------------------------------
	//PUBLIC METHODS
	//-------------------------------------------------------------------------
	@Override
	public List<String> suggestion(String text) {
		// ":" => set of allDescriptor's objectType.alias 
		// ":"<token> => set of allDescriptor's objectType.alias matching token 
		
		// ":"<valid-alias>. => list of objectType.alias descriptors   
		// ":"<valid-alias>.<token> => list of objectType.alias descriptors with fieldName matching token  
		
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SearchCriteria parse(String text) {
		SearchExpressionParser parser = new SearchExpressionParser(text);
		List<SearchExpression> expressions = new ArrayList<>();
		SearchExpression exp = null;
		while( (exp = parser.parseNext() ) != null ) {
			expressions.add(exp);
		}

		return new SearchCriteria(expressions);
	}

	@Override
	public Set<DomainObject> search(SearchCriteria criteria) {
		SearchResultSet resultSet = new SearchResultSet(criteria);
		
		Map<DomainObject, List<SearchableObjectField>> objectFieldMaps = searchContext.getObjectFieldMap();
		
		Iterator<Entry<DomainObject, List<SearchableObjectField>>> objectFields = objectFieldMaps.entrySet().iterator();
		while( objectFields.hasNext()) {
			Entry<DomainObject, List<SearchableObjectField>> object = objectFields.next();
			resultSet.match(object.getKey(), object.getValue());
		}
		return resultSet.getResult();
	}

	@Override
	public void update( DomainObjectChangesHolder objects ) {
		initialize();
	}

	public void initialize() {
		if ( objectRegistry == null ) {
			log.info("Initialization without objectRegistry.");
			return;
		}
		SearchContext ctx = new SearchContext(); 
		
		Traversal.traverse( getObjectRegistry().getServiceProviders(), ctx, new TraversalFunction<ServiceProviderDO, SearchContext>() {

			@Override
			public void visit(ServiceProviderDO object, TraversalContextHolder<SearchContext> holder) {
				
				sof(holder.getResult(), object, ServiceProviderSO.SUBJECT, object.getSubjectIdentifier());
				sof(holder.getResult(), object, ServiceProviderSO.MAS_HOSTNAME, object.getMasHostname());
				sof(holder.getResult(), object, ServiceProviderSO.MAS_PORT, object.getMasPort());
			}
		});
		
		// atomically replace the existing searchContext with the newly constructed one.
		searchContext=ctx;
	}

    //-------------------------------------------------------------------------
	//PROTECTED METHODS
	//-------------------------------------------------------------------------

	//-------------------------------------------------------------------------
	//PRIVATE METHODS
	//-------------------------------------------------------------------------

	private void sof( SearchContext ctx, DomainObject object, FieldDescriptor field, Calendar cal ) {
		if ( cal != null ) {
			ctx.add(new SearchableObjectField(object, field, cal));
		}
	}
	
	private void sof( SearchContext ctx, DomainObject object, FieldDescriptor field, Number num ) {
		if ( num != null ) {
			ctx.add(new SearchableObjectField(object, field, num));
		}
	}
	
	private void sof( SearchContext ctx, DomainObject object, FieldDescriptor field, String str ) {
		if ( str != null ) {
			ctx.add(new SearchableObjectField(object, field, str));
		}
	}
	
	private static class SearchContext {
		private Map<DomainObjectType,List<DomainObject>> objectTypeMap = new HashMap<>();
		private Map<DomainObject, List<SearchableObjectField>> objectFieldMap = new HashMap<>();
		
		public SearchContext() {
			objectTypeMap.put(DomainObjectType.ServiceProvider, new ArrayList<DomainObject>());
			//TODO each objectType
		}
		
		public void add( SearchableObjectField sof ) {
			List<DomainObject> list = objectTypeMap.get(sof.field.getObjectType());
			list.add(sof.object);
			
			List<SearchableObjectField> fields = objectFieldMap.get(sof.object);
			if ( fields == null ) {
				fields = new ArrayList<SearchableObjectField>();
				objectFieldMap.put(sof.object, fields);
			}
			fields.add(sof);
		}
		
		public Map<DomainObjectType, List<DomainObject>> getObjectTypeMap() {
			return objectTypeMap;
		}

		public Map<DomainObject, List<SearchableObjectField>> getObjectFieldMap() {
			return objectFieldMap;
		}

	}
	
	//-------------------------------------------------------------------------
	//PUBLIC ACCESSORS (GETTERS / SETTERS)
	//-------------------------------------------------------------------------

	public ObjectRegistry getObjectRegistry() {
		return objectRegistry;
	}

	public void setObjectRegistry(ObjectRegistry objectRegistry) {
		this.objectRegistry = objectRegistry;
	}

}
