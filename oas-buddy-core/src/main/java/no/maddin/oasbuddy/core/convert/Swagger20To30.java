package no.maddin.oasbuddy.core.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import no.maddin.oasbuddy.core.document.JsonNodes;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.document.SchemaWalker;
import no.maddin.oasbuddy.core.model.ComponentReferences;
import no.maddin.oasbuddy.core.model.HttpMethod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Swagger 2.0 → OAS 3.0: the import. 2.0 is never written, so there is no step back, and nothing
 * set aside here is ever restored; it is kept as {@code x-oasbuddy-<keyword>} (see {@link Kept}) only
 * so the user can see what did not carry over.
 *
 * <ul>
 *   <li>{@code host}, {@code basePath}, {@code schemes} become {@code servers}; an operation's own
 *       {@code schemes} become its {@code servers}.</li>
 *   <li>{@code definitions}, {@code parameters}, {@code responses}, {@code securityDefinitions} move
 *       into {@code components} ({@code schemas}, {@code parameters} + {@code requestBodies},
 *       {@code responses}, {@code securitySchemes}), and every {@code $ref} follows them.</li>
 *   <li>A body parameter becomes the {@code requestBody}; {@code formData} parameters become one
 *       form-encoded request body whose schema has a property per parameter. A path-level one applies
 *       to every operation of the path, as it did in 2.0.</li>
 *   <li>{@code consumes}/{@code produces} become the media types of request and response
 *       {@code content}; a response's {@code examples} become each media type's {@code example}.</li>
 *   <li>Parameters and headers: see {@link Swagger20Parameters}.</li>
 *   <li>Schemas: {@code type: file} becomes a binary string, a string {@code discriminator} its
 *       {@code propertyName}, and the vendor extension {@code x-nullable} is read as
 *       {@code nullable}.</li>
 *   <li>Security: {@code basic} becomes {@code http}/{@code basic}; an oauth2 {@code flow} with its
 *       URLs and scopes becomes {@code flows}.</li>
 * </ul>
 *
 * <p>Conflict locations name the place in the 2.0 document being imported.
 */
public final class Swagger20To30 implements ConversionStep {

    static final String DEFAULT_MEDIA_TYPE = "application/json";
    static final String MULTIPART = "multipart/form-data";
    static final String URLENCODED = "application/x-www-form-urlencoded";

    private static final String PARAMETERS = "parameters";
    private static final String RESPONSES = "responses";
    private static final String DEFINITIONS = "definitions";
    private static final String SECURITY_DEFINITIONS = "securityDefinitions";
    private static final String CONSUMES = "consumes";
    private static final String PRODUCES = "produces";
    private static final String SCHEMES = "schemes";
    private static final String SERVERS = "servers";
    private static final String CONTENT = "content";
    private static final String REQUEST_BODY = "requestBody";
    private static final String REF = "$ref";
    private static final String TYPE = "type";

    @Override
    public OasVersion from() {
        return OasVersion.V2_0;
    }

    @Override
    public OasVersion to() {
        return OasVersion.V3_0;
    }

    @Override
    public List<Conflict> apply(ObjectNode root) {
        return new Import(root).run();
    }

    /** One run of the step; holds what the document declares globally. */
    private static final class Import {

        private final ObjectNode root;
        private final List<Conflict> conflicts = new ArrayList<>();
        private final List<String> consumes;
        private final List<String> produces;
        private final ObjectNode globalParameters;
        private final String serverBase;
        private boolean assumedRequestType;
        private boolean assumedResponseType;

        Import(ObjectNode root) {
            this.root = root;
            this.consumes = texts(root.get(CONSUMES));
            this.produces = texts(root.get(PRODUCES));
            this.globalParameters = root.get(PARAMETERS) instanceof ObjectNode parameters
                    ? parameters.deepCopy() : JsonNodeFactory.instance.objectNode();
            String host = text(root, "host");
            String basePath = text(root, "basePath");
            String path = basePath == null || basePath.equals("/") ? "" : basePath;
            this.serverBase = host == null ? null : host + path;
        }

        List<Conflict> run() {
            SchemaWalker.walk(root, OasVersion.V2_0, this::convertSchema);
            if (root.get("paths") instanceof ObjectNode paths) {
                for (String path : JsonNodes.fieldNames(paths)) {
                    if (!path.startsWith("x-") && paths.get(path) instanceof ObjectNode item) {
                        convertPathItem(item, List.of("paths", path));
                    }
                }
            }
            buildComponents();
            ComponentReferences.rewriteAll(root, Import::rewriteRef);
            convertServers();
            root.remove(List.of(CONSUMES, PRODUCES));
            JsonNodes.renameField(root, "swagger", "openapi");
            root.put("openapi", OasVersion.V3_0.newDocumentVersion());
            if (assumedRequestType) {
                conflicts.add(Conflict.at(List.of(CONSUMES), "the document declares no consumes",
                        "request bodies are declared as " + DEFAULT_MEDIA_TYPE));
            }
            if (assumedResponseType) {
                conflicts.add(Conflict.at(List.of(PRODUCES), "the document declares no produces",
                        "responses are declared as " + DEFAULT_MEDIA_TYPE));
            }
            return conflicts;
        }

        private void convertSchema(ObjectNode schema, List<String> location) {
            if (schema.has("x-nullable") && !schema.has("nullable")) {
                JsonNodes.renameField(schema, "x-nullable", "nullable");
                conflicts.add(Conflict.at(Kept.append(location, "x-nullable"),
                        "x-nullable is a vendor extension, not part of Swagger 2.0", "read as nullable"));
            }
            if ("file".equals(schema.path(TYPE).asText())) {
                schema.put(TYPE, "string");
                JsonNodes.setAfter(schema, TYPE, "format", TextNode.valueOf("binary"));
            }
            if (schema.get("discriminator") instanceof TextNode property) {
                schema.set("discriminator", JsonNodeFactory.instance.objectNode().set("propertyName", property));
            }
        }

        private void convertPathItem(ObjectNode item, List<String> location) {
            List<ObjectNode> bodies = new ArrayList<>();
            Map<String, ObjectNode> forms = new LinkedHashMap<>();
            splitParameters(item, Kept.append(location, PARAMETERS), bodies, forms);
            ObjectNode pathBody = bodies.isEmpty() ? null : bodies.getFirst();
            for (HttpMethod method : HttpMethod.values()) {
                if (item.get(method.fieldName()) instanceof ObjectNode operation) {
                    convertOperation(operation, Kept.append(location, method.fieldName()), pathBody, forms);
                }
            }
        }

        private void convertOperation(ObjectNode operation, List<String> location, ObjectNode pathBody,
                                      Map<String, ObjectNode> pathForms) {
            List<String> requestTypes = operation.has(CONSUMES) ? texts(operation.get(CONSUMES)) : consumes;
            List<String> responseTypes = operation.has(PRODUCES) ? texts(operation.get(PRODUCES)) : produces;
            List<ObjectNode> bodies = new ArrayList<>();
            Map<String, ObjectNode> forms = new LinkedHashMap<>(pathForms);
            splitParameters(operation, Kept.append(location, PARAMETERS), bodies, forms);
            if (bodies.isEmpty() && pathBody != null) {
                bodies.add(pathBody);
            }
            ObjectNode requestBody = requestBodyOf(operation, location, bodies, forms, requestTypes);
            if (requestBody != null) {
                placeRequestBody(operation, requestBody);
            }
            if (operation.get(RESPONSES) instanceof ObjectNode responses) {
                eachObject(responses, (code, response) ->
                        convertResponse(response, responseTypes, Kept.append(location, RESPONSES, code)));
            }
            operation.remove(List.of(CONSUMES, PRODUCES));
            convertOperationSchemes(operation, location);
        }

        /** The operation's request body: its body parameter, or else its form fields; {@code null} for neither. */
        private ObjectNode requestBodyOf(ObjectNode operation, List<String> location, List<ObjectNode> bodies,
                                         Map<String, ObjectNode> forms, List<String> requestTypes) {
            if (bodies.isEmpty()) {
                return forms.isEmpty() ? null : formBody(forms, requestTypes, Kept.append(location, PARAMETERS));
            }
            keepAside(operation, bodies.subList(1, bodies.size()), location,
                    "OAS 3.0 allows one request body; this operation has several body parameters");
            keepAside(operation, new ArrayList<>(forms.values()), location,
                    "a body parameter and formData parameters cannot both describe the request body");
            return requestBody(bodies.getFirst(), requestTypes);
        }

        private void convertOperationSchemes(ObjectNode operation, List<String> location) {
            if (!operation.has(SCHEMES)) {
                return;
            }
            ArrayNode servers = servers(texts(operation.get(SCHEMES)));
            if (servers == null) {
                Kept.setAside(operation, SCHEMES, location, "schemes without a host form no server url",
                        null, conflicts);
            } else {
                JsonNodes.replaceFields(operation, List.of(SCHEMES), SERVERS, servers);
            }
        }

        /**
         * Takes the body and formData parameters out of {@code owner}'s list, resolving a reference to a
         * global one, and converts the rest in place. Removes the list once nothing is left in it.
         */
        private void splitParameters(ObjectNode owner, List<String> location, List<ObjectNode> bodies,
                                     Map<String, ObjectNode> forms) {
            if (!(owner.get(PARAMETERS) instanceof ArrayNode parameters)) {
                return;
            }
            List<Integer> taken = new ArrayList<>();
            for (int i = 0; i < parameters.size(); i++) {
                if (parameters.get(i) instanceof ObjectNode parameter
                        && takeParameter(parameter, Kept.append(location, "[" + i + "]"), bodies, forms)) {
                    taken.add(i);
                }
            }
            for (int i = taken.size() - 1; i >= 0; i--) {
                parameters.remove(taken.get(i).intValue());
            }
            if (parameters.isEmpty()) {
                owner.remove(PARAMETERS);
            }
        }

        /**
         * Collects a body or formData parameter, resolving a reference to a global one, and returns
         * {@code true} for those; converts any other parameter in place and returns {@code false}.
         */
        private boolean takeParameter(ObjectNode parameter, List<String> location, List<ObjectNode> bodies,
                                      Map<String, ObjectNode> forms) {
            String globalName = globalName(parameter);
            ObjectNode global = globalName == null ? null : JsonNodes.objectOrNull(globalParameters.get(globalName));
            ObjectNode resolved = global != null ? global : parameter;
            switch (resolved.path("in").asText()) {
                case "body" -> bodies.add(global != null ? refTo("requestBodies", globalName) : parameter);
                case "formData" -> forms.put(resolved.path("name").asText(), resolved.deepCopy());
                default -> {
                    if (!parameter.has(REF)) {
                        Swagger20Parameters.convertParameter(parameter, location, conflicts);
                    }
                    return false;
                }
            }
            return true;
        }

        /** A 3.0 request body from a 2.0 body parameter (or the reference standing for a global one). */
        private ObjectNode requestBody(ObjectNode body, List<String> requestTypes) {
            if (body.has(REF)) {
                // a path-level body reaches several operations: each gets its own node
                return body.deepCopy();
            }
            ObjectNode requestBody = JsonNodeFactory.instance.objectNode();
            if (body.has("description")) {
                requestBody.set("description", body.get("description"));
            }
            ObjectNode content = requestBody.putObject(CONTENT);
            for (String mediaType : requestTypes(requestTypes)) {
                ObjectNode entry = content.putObject(mediaType);
                if (body.get("schema") != null) {
                    entry.set("schema", body.get("schema").deepCopy());
                }
            }
            if (body.path("required").asBoolean(false)) {
                requestBody.put("required", true);
            }
            copyExtensions(body, requestBody);
            return requestBody;
        }

        /** One request body for every formData parameter: an object schema with a property per parameter. */
        private ObjectNode formBody(Map<String, ObjectNode> forms, List<String> requestTypes, List<String> location) {
            ObjectNode schema = JsonNodeFactory.instance.objectNode().put(TYPE, "object");
            ObjectNode properties = schema.putObject("properties");
            ArrayNode required = JsonNodeFactory.instance.arrayNode();
            ObjectNode encoding = JsonNodeFactory.instance.objectNode();
            boolean file = false;
            for (Map.Entry<String, ObjectNode> form : forms.entrySet()) {
                String name = form.getKey();
                ObjectNode parameter = form.getValue();
                List<String> at = Kept.append(location, name);
                ObjectNode property = Swagger20Parameters.schemaOf(parameter, at, conflicts);
                if (parameter.has("description")) {
                    property.set("description", parameter.get("description"));
                }
                properties.set(name, property);
                file |= "file".equals(parameter.path(TYPE).asText());
                if (parameter.path("required").asBoolean(false)) {
                    required.add(name);
                }
                formEncoding(parameter, at).ifPresent(style -> encoding.set(name, style));
            }
            if (!required.isEmpty()) {
                schema.set("required", required);
            }
            List<String> formTypes = requestTypes.stream()
                    .filter(type -> type.equals(MULTIPART) || type.equals(URLENCODED)).toList();
            if (formTypes.isEmpty()) {
                formTypes = List.of(file ? MULTIPART : URLENCODED);
            }
            ObjectNode requestBody = JsonNodeFactory.instance.objectNode();
            ObjectNode content = requestBody.putObject(CONTENT);
            for (String mediaType : formTypes) {
                ObjectNode entry = content.putObject(mediaType);
                entry.set("schema", schema.deepCopy());
                if (mediaType.equals(URLENCODED) && !encoding.isEmpty()) {
                    entry.set("encoding", encoding.deepCopy());
                }
            }
            if (!required.isEmpty()) {
                requestBody.put("required", true);
            }
            return requestBody;
        }

        /** How a form-encoded array is serialized: 2.0's {@code collectionFormat} as an encoding entry. */
        private Optional<ObjectNode> formEncoding(ObjectNode parameter, List<String> location) {
            if (!"array".equals(parameter.path(TYPE).asText())) {
                return Optional.empty();
            }
            String format = parameter.path(Swagger20Parameters.COLLECTION_FORMAT).asText("csv");
            ObjectNode style = JsonNodeFactory.instance.objectNode();
            switch (format) {
                case "multi" -> {
                    return Optional.empty();
                }
                case "csv" -> style.put("style", "form").put("explode", false);
                case "ssv" -> style.put("style", "spaceDelimited");
                case "pipes" -> style.put("style", "pipeDelimited");
                default -> conflicts.add(Conflict.at(Kept.append(location, Swagger20Parameters.COLLECTION_FORMAT),
                        "collectionFormat " + format + " has no OAS 3.0 equivalent here",
                        "dropped; the default serialization applies"));
            }
            return style.isEmpty() ? Optional.empty() : Optional.of(style);
        }

        /** Where 3.0 writes it: after the parameters, or else just before the responses. */
        private static void placeRequestBody(ObjectNode operation, ObjectNode requestBody) {
            List<String> fields = JsonNodes.fieldNames(operation);
            int responses = fields.indexOf(RESPONSES);
            if (operation.has(PARAMETERS)) {
                JsonNodes.setAfter(operation, PARAMETERS, REQUEST_BODY, requestBody);
            } else if (responses > 0) {
                JsonNodes.setAfter(operation, fields.get(responses - 1), REQUEST_BODY, requestBody);
            } else if (responses == 0) {
                JsonNode responsesNode = operation.get(RESPONSES);
                JsonNodes.replaceFields(operation, List.of(RESPONSES), REQUEST_BODY, requestBody);
                JsonNodes.setAfter(operation, REQUEST_BODY, RESPONSES, responsesNode);
            } else {
                operation.set(REQUEST_BODY, requestBody);
            }
        }

        /** Parameters that cannot become part of the request body, kept on the operation and reported. */
        private void keepAside(ObjectNode operation, List<ObjectNode> parameters, List<String> location,
                               String problem) {
            if (parameters.isEmpty()) {
                return;
            }
            ArrayNode kept = JsonNodes.arrayChild(operation, Kept.name(PARAMETERS));
            for (ObjectNode parameter : parameters) {
                kept.add(parameter);
                String name = parameter.has(REF) ? parameter.path(REF).asText() : parameter.path("name").asText();
                conflicts.add(Conflict.at(Kept.append(location, PARAMETERS, name), problem,
                        "kept in " + Kept.name(PARAMETERS)));
            }
        }

        private void convertResponse(ObjectNode response, List<String> responseTypes, List<String> location) {
            if (response.has(REF)) {
                return;
            }
            if (response.get("headers") instanceof ObjectNode headers) {
                eachObject(headers, (name, header) ->
                        Swagger20Parameters.convertHeader(header, Kept.append(location, "headers", name), conflicts));
            }
            JsonNode schema = response.get("schema");
            JsonNode examples = response.get("examples");
            if (schema == null && !(examples instanceof ObjectNode)) {
                return;
            }
            ObjectNode content = responseContent(schema, examples, responseTypes);
            JsonNodes.replaceFields(response, List.of("schema", "examples"), CONTENT, content);
        }

        /** A media type per produced type carrying the schema, plus each example under its media type. */
        private ObjectNode responseContent(JsonNode schema, JsonNode examples, List<String> responseTypes) {
            ObjectNode content = JsonNodeFactory.instance.objectNode();
            if (schema != null) {
                for (String mediaType : responseTypes(responseTypes)) {
                    content.putObject(mediaType).set("schema", schema.deepCopy());
                }
            }
            if (examples instanceof ObjectNode byMediaType) {
                for (String mediaType : JsonNodes.fieldNames(byMediaType)) {
                    ObjectNode entry = content.get(mediaType) instanceof ObjectNode existing
                            ? existing : content.putObject(mediaType);
                    if (schema != null && !entry.has("schema")) {
                        entry.set("schema", schema.deepCopy());
                    }
                    entry.set("example", byMediaType.get(mediaType));
                }
            }
            return content;
        }

        /** {@code components}, in place of the first 2.0 section it replaces, its sections in document order. */
        private void buildComponents() {
            List<String> sources = List.of(DEFINITIONS, PARAMETERS, RESPONSES, SECURITY_DEFINITIONS);
            ObjectNode components = JsonNodeFactory.instance.objectNode();
            for (String key : JsonNodes.fieldNames(root)) {
                if (root.get(key) instanceof ObjectNode section) {
                    addComponentSection(key, section, components);
                }
            }
            if (components.isEmpty()) {
                root.remove(sources);
            } else {
                JsonNodes.replaceFields(root, sources, "components", components);
            }
        }

        private void addComponentSection(String key, ObjectNode section, ObjectNode components) {
            switch (key) {
                case DEFINITIONS -> components.set("schemas", section);
                case PARAMETERS -> componentParameters(section, components);
                case RESPONSES -> {
                    eachObject(section, (name, response) ->
                            convertResponse(response, produces, List.of(RESPONSES, name)));
                    components.set(RESPONSES, section);
                }
                case SECURITY_DEFINITIONS -> {
                    eachObject(section, (name, scheme) ->
                            convertSecurityScheme(scheme, List.of(SECURITY_DEFINITIONS, name)));
                    components.set("securitySchemes", section);
                }
                default -> {
                    // not a section 2.0 keeps reusable things in
                }
            }
        }

        private void componentParameters(ObjectNode section, ObjectNode components) {
            ObjectNode parameters = JsonNodeFactory.instance.objectNode();
            ObjectNode requestBodies = JsonNodeFactory.instance.objectNode();
            for (String name : JsonNodes.fieldNames(section)) {
                if (!(section.get(name) instanceof ObjectNode parameter)) {
                    continue;
                }
                List<String> location = List.of(PARAMETERS, name);
                switch (parameter.path("in").asText()) {
                    case "body" -> requestBodies.set(name, requestBody(parameter, consumes));
                    case "formData" -> conflicts.add(Conflict.at(location,
                            "OAS 3.0 cannot reuse a single form field",
                            "inlined into the request body of every operation referring to it"));
                    default -> {
                        Swagger20Parameters.convertParameter(parameter, location, conflicts);
                        parameters.set(name, parameter);
                    }
                }
            }
            if (!parameters.isEmpty()) {
                components.set(PARAMETERS, parameters);
            }
            if (!requestBodies.isEmpty()) {
                components.set("requestBodies", requestBodies);
            }
        }

        private void convertSecurityScheme(ObjectNode scheme, List<String> location) {
            String type = scheme.path(TYPE).asText();
            if (type.equals("basic")) {
                scheme.put(TYPE, "http");
                JsonNodes.setAfter(scheme, TYPE, "scheme", TextNode.valueOf("basic"));
            } else if (type.equals("oauth2")) {
                String flow = scheme.path("flow").asText();
                String name = switch (flow) {
                    case "implicit", "password" -> flow;
                    case "application" -> "clientCredentials";
                    case "accessCode" -> "authorizationCode";
                    default -> null;
                };
                if (name == null) {
                    Kept.setAside(scheme, "flow", location, "an unknown oauth2 flow cannot be converted", null,
                            conflicts);
                    return;
                }
                ObjectNode flowObject = JsonNodeFactory.instance.objectNode();
                for (String url : List.of("authorizationUrl", "tokenUrl")) {
                    if (scheme.has(url)) {
                        flowObject.set(url, scheme.get(url));
                    }
                }
                flowObject.set("scopes", scheme.get("scopes") instanceof ObjectNode scopes
                        ? scopes : JsonNodeFactory.instance.objectNode());
                ObjectNode flows = JsonNodeFactory.instance.objectNode();
                flows.set(name, flowObject);
                JsonNodes.replaceFields(scheme, List.of("flow", "authorizationUrl", "tokenUrl", "scopes"),
                        "flows", flows);
            }
        }

        private void convertServers() {
            List<String> keys = List.of("host", "basePath", SCHEMES);
            List<String> schemes = texts(root.get(SCHEMES));
            ArrayNode servers;
            if (serverBase != null) {
                if (schemes.isEmpty()) {
                    schemes = List.of("https");
                    conflicts.add(Conflict.at(List.of(SCHEMES), "the document declares no schemes",
                            "the server url assumes https"));
                }
                servers = servers(schemes);
            } else if (root.has("basePath")) {
                servers = JsonNodeFactory.instance.arrayNode();
                servers.addObject().put("url", text(root, "basePath"));
                Kept.setAside(root, SCHEMES, List.of(), "schemes without a host form no server url", null,
                        conflicts);
            } else {
                Kept.setAside(root, SCHEMES, List.of(), "schemes without a host form no server url", null,
                        conflicts);
                return;
            }
            JsonNodes.replaceFields(root, keys.stream().filter(root::has).toList(), SERVERS, servers);
        }

        /** One server per scheme on the document's host and base path, or {@code null} without a host. */
        private ArrayNode servers(List<String> schemes) {
            if (serverBase == null) {
                return null;
            }
            ArrayNode servers = JsonNodeFactory.instance.arrayNode();
            schemes.forEach(scheme -> servers.addObject().put("url", scheme + "://" + serverBase));
            return servers;
        }

        private List<String> requestTypes(List<String> declared) {
            if (declared.isEmpty()) {
                assumedRequestType = true;
                return List.of(DEFAULT_MEDIA_TYPE);
            }
            return declared;
        }

        private List<String> responseTypes(List<String> declared) {
            if (declared.isEmpty()) {
                assumedResponseType = true;
                return List.of(DEFAULT_MEDIA_TYPE);
            }
            return declared;
        }

        private static String rewriteRef(String ref) {
            for (String[] move : new String[][]{
                    {"#/definitions/", "#/components/schemas/"},
                    {"#/parameters/", "#/components/parameters/"},
                    {"#/responses/", "#/components/responses/"}}) {
                if (ref.startsWith(move[0])) {
                    return move[1] + ref.substring(move[0].length());
                }
            }
            return null;
        }

        private static String globalName(ObjectNode parameter) {
            String ref = parameter.path(REF).asText(null);
            return ref != null && ref.startsWith("#/parameters/") ? ref.substring("#/parameters/".length()) : null;
        }

        /** Every entry of {@code map} whose value is an object, in order, skipping {@code x-} extensions. */
        private static void eachObject(ObjectNode map, BiConsumer<String, ObjectNode> action) {
            for (String name : JsonNodes.fieldNames(map)) {
                if (!name.startsWith("x-") && map.get(name) instanceof ObjectNode value) {
                    action.accept(name, value);
                }
            }
        }

        private static ObjectNode refTo(String section, String name) {
            return JsonNodeFactory.instance.objectNode().put(REF, ComponentReferences.ref(section, name));
        }

        private static void copyExtensions(ObjectNode from, ObjectNode to) {
            for (String key : JsonNodes.fieldNames(from)) {
                if (key.startsWith("x-")) {
                    to.set(key, from.get(key));
                }
            }
        }

        private static String text(ObjectNode node, String field) {
            JsonNode value = node.get(field);
            return value != null && value.isTextual() ? value.asText() : null;
        }

        private static List<String> texts(JsonNode array) {
            List<String> values = new ArrayList<>();
            if (array instanceof ArrayNode elements) {
                elements.forEach(element -> values.add(element.asText()));
            }
            return values;
        }
    }
}
