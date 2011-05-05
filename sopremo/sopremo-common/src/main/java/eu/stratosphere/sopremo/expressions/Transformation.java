package eu.stratosphere.sopremo.expressions;

import java.util.ArrayList;
import java.util.List;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;

import eu.stratosphere.dag.Navigator;
import eu.stratosphere.reflect.TypeHandler;
import eu.stratosphere.reflect.TypeSpecificHandler;

public class Transformation extends Mapping {
	public static final Transformation IDENTITY = new Transformation();

	public static final Transformation CONCATENATION = new Transformation() {
		@Override
		public JsonNode evaluate(JsonNode... nodes) {
			ObjectNode objectNode = NODE_FACTORY.objectNode();
			for (JsonNode jsonNode : nodes)
				if (!jsonNode.isNull())
					// deepCopy(objectNode, jsonNode);
					objectNode.putAll((ObjectNode) jsonNode);
			return objectNode;
		}

		// private void deepCopy(ObjectNode objectNode, JsonNode jsonNode) {
		// for (int index = 0; index > jsonNode.size(); index++) {
		//
		// }
		// }
	};

	private List<Mapping> mappings = new ArrayList<Mapping>();

	private boolean array;

	public Transformation() {
		this(NO_TARGET);
	}

	public Transformation(String target) {
		super(target);
	}

	public EvaluableExpression asPath() {
		if (this.getMappingSize() == 0)
			return null;

		Mapping mapping = this.getMapping(0);
		if (mapping instanceof ValueAssignment)
			return ((ValueAssignment) mapping).getTransformation();
		return null;
	}

	// public Transformation(boolean array) {
	// }

	public void addMapping(Mapping mapping) {
		this.mappings.add(mapping);
	}

	public List<Mapping> getMappings() {
		return this.mappings;
	}

	public int getMappingSize() {
		return this.mappings.size();
	}

	public Mapping getMapping(int index) {
		return this.mappings.get(index);
	}

	public Object simplify() {
		if (this.getTarget() == null && this.mappings.size() == 1 && this.mappings.get(0).getTarget() == null
			&& this.mappings.get(0) instanceof ValueAssignment)
			return ((ValueAssignment) this.mappings.get(0)).getTransformation();
		return this;
	}

	@Override
	protected void toString(StringBuilder builder) {
		if (this.getTarget() != NO_TARGET)
			builder.append(this.getTarget()).append("=");
		builder.append("[");
		for (int index = 0; index < this.mappings.size(); index++) {
			if (index > 0)
				builder.append(", ");
			this.mappings.get(index).toString(builder);
		}
		builder.append("]");
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (this.array ? 1231 : 1237);
		result = prime * result + this.mappings.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (this.getClass() != obj.getClass())
			return false;
		Transformation other = (Transformation) obj;
		return super.equals(obj) && this.array == other.array && this.mappings.equals(other.mappings);
	}

	private static TypeSpecificHandler<Mapping, Mapping, TypeHandler<Mapping, Mapping>> PathReplacer = new TypeSpecificHandler<Mapping, Mapping, TypeHandler<Mapping, Mapping>>();

	static {
		PathReplacer.register(new TypeHandler<ValueAssignment, Mapping>() {
			public ValueAssignment replace(ValueAssignment assignment, List<Mapping> mapping,
					Path toReplace,
					Path replaceFragment) {
				assignment.setTransformation(Path.replace((Path) assignment.getTransformation(), toReplace,
					replaceFragment));
				return assignment;
			}
		}, ValueAssignment.class);
	}

	public void replace(EvaluableExpression toReplace, EvaluableExpression replaceFragment) {
		PathReplacer.handleRecursively(new MappingNavigator(), this, toReplace, replaceFragment);
	}

	private static final class MappingNavigator implements Navigator<Mapping> {
		private static final Iterable<Mapping> EMPTY = new ArrayList<Mapping>();

		@Override
		public Iterable<Mapping> getConnectedNodes(Mapping node) {
			if (node instanceof Transformation)
				return ((Transformation) node).getMappings();
			return EMPTY;
		}
	}

	@Override
	public JsonNode evaluate(JsonNode node) {
		if (this == IDENTITY)
			return node;
		ObjectNode transformedNode = OBJECT_MAPPER.createObjectNode();
		for (Mapping mapping : this.mappings)
			transformedNode.put(mapping.getTarget(), mapping.evaluate(node));
		return transformedNode;
	}
}
