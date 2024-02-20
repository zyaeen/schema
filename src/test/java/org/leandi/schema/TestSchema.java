package org.leandi.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.leandi.schema.deploy.*;
import org.leandi.schema.domain.*;
import org.leandi.schema.domain.Domain;
import org.leandi.schema.domain.basetypes.*;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.*;
import static org.leandi.schema.deploy.DbTypeType.*;
import static org.leandi.schema.domain.basetypes.DataRange.*;

/**
 * Тестируем создание проекта, его выгрузку.
 */
class TestSchema {

    private static final String AUTHOR = "author";
    private static final int MIN_DESCRIPTOR_LENGTH = 5;
    private static final int MAX_DESCRIPTOR_LENGTH = 10;
    private static final DataRange[] DATA_RANGES = {STRING, BIGINT}; //TIME, DATE
    private static final String[] AUTHORS = {"Ivanov Ivan Ivanovich", "Komov Vasiliy Ivanovich"};
    private static String[] words;
    private static final int DBT_LENGTH = DATA_RANGES.length;
    private static final Identity IDENTITY = Identity.BIGINT;

    private static final Random RND = new SecureRandom();
    private static final ZoneId ZONE_ID = ZoneId.systemDefault();
    private static final int DOMAINS = 2;
    private static final int ANCHOR_MNEMONIC_LENGTH = 2;
    private static final int KNOT_ATTR_MNEMONIC_LENGTH = 3;
    private static final int MIN_KNOTS = 2;
    private static final int MAX_KNOTS = 5;
    private static final int MIN_KNOT_VALUES = 2;
    private static final int MAX_KNOT_VALUES = 10;
    private static final int MIN_ANCHORS = 4;
    private static final int MAX_ANCHORS = 8;

    private static final int MIN_ATTRIBUTES = 2;
    private static final int MAX_ATTRIBUTES = 4;

    private static final int MIN_ATTR_COLUMNS = 0;
    private static final int MAX_ATTR_COLUMNS = 2;

    private static final int MIN_ANCHOR_COLUMNS = 0;
    private static final int MAX_ANCHOR_COLUMNS = 1;

    private static final int MIN_TIE_COLUMNS = 0;
    private static final int MAX_TIE_COLUMNS = 2;

    private static final int MIN_VERTICAL_PROPERTIES = 20;
    private static final int MAX_VERTICAL_PROPERTIES = 40;

    private static final int MIN_VERTICAL_GROUPS = 2;
    private static final int MAX_VERTICAL_GROUPS = 4;

    private static final int MIN_ITEMS = 2;
    private static final int MAX_ITEMS = 4;
    private static final int MIN_DOMAINS = 3;
    private static final int MAX_DOMAINS = 4;
    private static final int MIN_DB_HOSTS = 1;
    private static final int MAX_DB_HOSTS = 2;

    private static final int MIN_FS_HOSTS = 1;
    private static final int MAX_FS_HOSTS = 2;
    private static final int MIN_CLUSTER_ID = 1;
    private static final int MAX_CLUSTER_ID = 5;
    private static final int MIN_LABEL_LENGTH = 5;
    private static final int MAX_LABEL_LENGTH = 10;
    private static final int MIN_PORT = 1000;
    private static final int MAX_PORT = 9000;

    private Deploy deployModel;

    private static final DbTypeType[] DB_TYPES = {POSTGRES, HSQLDB, ORACLE};

    private static DatatypeFactory FACTORY;
    private Project project;
    private final Domain[] domains = new Domain[2];

    @BeforeAll
    public static void before() throws DatatypeConfigurationException {
        FACTORY = DatatypeFactory.newInstance();
        int bound = RND.nextInt(200) + 200;
        words = new String[bound];
        for (int i = 0; i < bound; i++) {
            words[i] = RandomStringUtils.randomAlphabetic(RND.nextInt(10) + 3);
        }
    }

    @Test
    void marshallRandomDeployModel() throws JAXBException {

        String deployName = "Deploy Model";

        deployModel = new Deploy();
        deployModel.setProject(UUID.randomUUID().toString());
        deployModel.setName(deployName);
        deployModel.setShortName("deployModel");
        deployModel.setAuthor(getAuthor());
        deployModel.setVersion("v" + RND.nextInt(10));
        deployModel.setDateTime(getXsdDate());
        deployModel.setNote("Note " + deployName);


        int maxDbHosts = getRandomNumberBetween(MIN_DB_HOSTS, MAX_DB_HOSTS);
        List<DbHost> dbHosts = new ArrayList<>();
        for (int count = 0; count < maxDbHosts; count++) {
            dbHosts.add(createRandomDbHost());
        }
        int maxFsHosts = getRandomNumberBetween(MIN_FS_HOSTS, MAX_FS_HOSTS);
        List<FsHost> fsHosts = new ArrayList<>();
        for (int count = 0; count < maxFsHosts; count++) {
            fsHosts.add(createRandomFsHost());
        }
        deployModel.getDbHost().addAll(dbHosts);
        deployModel.getFsHost().addAll(fsHosts);

        System.out.println("\n************************DEPLOY MODEL************************");
        SchemaUtils schemaUtils = SchemaUtils.builder().deploy(deployModel).build();
        System.out.println(schemaUtils.marshallDeployModel());
    }

    @Test
    void unmarshallDeployModel() throws JAXBException {
        InputStream resourceAsStream = TestSchema.class.getResourceAsStream("/deploy.xml");
        JAXBContext jaxbContext = JAXBContext.newInstance(Deploy.class);

        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        Source source = new StreamSource(resourceAsStream);
        JAXBElement<Deploy> element = unmarshaller.unmarshal(source, Deploy.class);

        Deploy deploy = element.getValue();

        assertNotEquals(0, deploy.getDbHost().size());
        assertNotEquals(0, deploy.getFsHost().size());

        for (FsHost fsHost : deploy.getFsHost()) {
            assertNotNull(fsHost.getHost());
            assertNotNull(fsHost.getFolder());
            assertNotNull(fsHost.getUserName());
            for (org.leandi.schema.deploy.Domain domain : fsHost.getDomain()){
                assertNotNull(domain.getShortName());
                for (DeployItem item : domain.getItem()){
                    assertFalse(StringUtils.isEmpty(item.getFqn()));
                }
            }
        }
        for (DbHost dbHost : deploy.getDbHost()) {
            assertNotNull(dbHost.getHost());
            assertNotNull(dbHost.getUserName());
            assertNotNull(dbHost.getPort());
            assertNotNull(dbHost.getDbName());
            assertNotNull(dbHost.getDbType());
            for (org.leandi.schema.deploy.Domain domain : dbHost.getDomain()){
                assertNotNull(domain.getShortName());
                for (DeployItem item : domain.getItem()){
                    assertFalse(StringUtils.isEmpty(item.getFqn()));
                }
            }
        }
    }

    @Test
    void generateDeploySchemaFromProject() throws JAXBException, JsonProcessingException {
        InputStream resourceAsStream = TestSchema.class.getResourceAsStream("/work_project.xml");
        JAXBContext context2 = JAXBContext.newInstance(Project.class);

        Unmarshaller march = context2.createUnmarshaller();
        Source source = new StreamSource(resourceAsStream);
        JAXBElement<Project> element = march.unmarshal(source, Project.class);

        SchemaUtils projectUtils = SchemaUtils.builder().project(element.getValue()).build();
        Project projectFromXml = projectUtils.getProject();

        Deploy deploy = new Deploy();
        deploy.setProject(projectFromXml.getUid());
        deploy.setAuthor(AUTHORS[0]);
        deploy.setDateTime(projectFromXml.getDateTime());

        int maxDbHosts = getRandomNumberBetween(MIN_DB_HOSTS, MAX_DB_HOSTS);
        List<DbHost> dbHosts = new ArrayList<>();
        for (int count = 0; count < maxDbHosts; count++) {
            DbHost dbHost = createRandomDbHost();
            dbHost.getDomain().clear();
            dbHosts.add(dbHost);
        }
        int maxFsHosts = getRandomNumberBetween(MIN_FS_HOSTS, MAX_FS_HOSTS);
        List<FsHost> fsHosts = new ArrayList<>();
        for (int count = 0; count < maxFsHosts; count++) {
            FsHost fsHost = createRandomFsHost();
            fsHost.getDomain().clear();
            fsHosts.add(fsHost);
        }

        for(Domain dom : projectFromXml.getDomain()){
            org.leandi.schema.deploy.Domain deployDomain = createDeployDomainFromProjectDomain(dom);
            int randomInt = RND.nextInt(101);
            if (randomInt % 2 == 0){
                dbHosts.get(randomInt % dbHosts.size()).getDomain().add(deployDomain);
            } else {
                fsHosts.get(randomInt % fsHosts.size()).getDomain().add(deployDomain);
            }
        }

        deploy.getDbHost().addAll(dbHosts);
        deploy.getFsHost().addAll(fsHosts);

        System.out.println("\n************************DEPLOY MODEL FROM PROJECT************************");
        SchemaUtils deployUtils = SchemaUtils.builder().deploy(deploy).build();
        System.out.println(deployUtils.marshallDeployModel());

    }

    @Test
    void unmarshallDomain() throws JAXBException {
        InputStream resourceAsStream = TestSchema.class.getResourceAsStream("/domain.xml");
        JAXBContext context2 = JAXBContext.newInstance(Domain.class);

        Unmarshaller march = context2.createUnmarshaller();
        Source source = new StreamSource(resourceAsStream);
        JAXBElement<Domain> element = march.unmarshal(source, Domain.class);
    }

    @Test
    void marshalRandomProject() throws JAXBException {

        project = new Project();
        project.setAuthor(AUTHOR);
        project.setDateTime(getXsdDate());
        project.setAuthor(getAuthor());
        project.setNote("Note ");
        project.setVersion("v" + RND.nextInt(10));
        project.setUid(UUID.randomUUID().toString());

        for (int count = 0; count < DOMAINS; count++) {
            domains[count] = createRandomDomainForDeploy(count);
            project.getDomain().add(domains[count]);
        }

        //Кросс-доменные связи
        for (int count = 0; count < DOMAINS; count++) {
            project.getConnexions().add(createRandomConnexion(project.getDomain()));
        }
        System.out.println("\n************************PROJECT WITH DOMAINS************************");

        SchemaUtils projectSchemaUtils = SchemaUtils.builder().project(project).build();
        System.out.println(projectSchemaUtils.marshallProject());

        System.out.println("\n************************DOMAIN************************");

        SchemaUtils domainSchemaUtils = SchemaUtils.builder().domain(createRandomDomainForDeploy(2)).build();
        System.out.println(domainSchemaUtils.marshall());

//        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
//        ClassLoader classLoader = TestSchema.class.getClassLoader();
//        Schema schema = factory.newSchema(new Source[]{
//                new StreamSource(classLoader.getResourceAsStream("xsd/leandi-project.xsd"))
//                , new StreamSource(classLoader.getResourceAsStream("xsd/leandi-domain.xsd"))
//                , new StreamSource(classLoader.getResourceAsStream("xsd/leandi-base-types.xsd"))
//        });
//        Validator validator = schema.newValidator();
//        validator.validate(new StreamSource(new StringReader(projectStr)));
    }

    private org.leandi.schema.deploy.Domain createDeployDomainFromProjectDomain(Domain projectDomain) {
        org.leandi.schema.deploy.Domain deployDomain = new org.leandi.schema.deploy.Domain();

        List<Anchor> anchors = projectDomain.getAnchor();

        deployDomain.setShortName(projectDomain.getShortName());

        List<DeployItem> deployItems = new ArrayList<>();

        int maxAnchors = getRandomNumberBetween(1, anchors.size());
        for (int anchorsCount = 0; anchorsCount < maxAnchors; anchorsCount++) {
            Anchor anchor = anchors.get(anchorsCount);
            DeployItem deployAnchorItem = createRandomDeployItem();
            deployAnchorItem.setFqn(anchor.getMnemonic());
            deployItems.add(deployAnchorItem);
            if (anchor.getAttribute().size() != 0){
                int maxAttributes = getRandomNumberBetween(1, anchor.getAttribute().size());
                for (int attrCount = 0; attrCount < maxAttributes; attrCount++) {
                    Attribute attribute = anchor.getAttribute().get(attrCount);
                    DeployItem deployAttrItem = createRandomDeployItem();
                    deployAttrItem.setFqn(anchor.getMnemonic() + "." + attribute.getMnemonic());
                    deployItems.add(deployAttrItem);
                }
            }
        }
        List<Knot> knots = projectDomain.getKnot();
        int maxKnots = getRandomNumberBetween(0, knots.size());
        for (int knotsCount = 0; knotsCount < maxKnots; knotsCount++) {
            Knot knot = knots.get(knotsCount);
            DeployItem deployAnchorItem = createRandomDeployItem();
            deployAnchorItem.setFqn(knot.getMnemonic());
            deployItems.add(deployAnchorItem);
        }
        deployDomain.getItem().addAll(deployItems);
        return deployDomain;
    }


    private Connexions createRandomConnexion(List<Domain> domains) {
        Connexions connexions = new Connexions();
        connexions.setUid(UUID.randomUUID().toString());
        connexions.setLayout(createRandomLayout());
        connexions.setDescription(createRandomDescription());
        connexions.setDescriptor(createRandomDescriptor());

        List<AnchorRole> anchorRole = connexions.getAnchorRole();
        Collections.shuffle(domains);

        Domain domain1 = domains.get(0);
        Domain domain2 = domains.get(1);
        AnchorRole anchorRole1;

        AnchorRole anchorRole2;

//        anchorRole.add(anchorRole1);
//        anchorRole.add(anchorRole2);

        return connexions;
    }

    private Domain createRandomDomainForDeploy(int domainCount) {
        String domainName = "Domain " + domainCount;
        Domain domain = new Domain();
        domain.setDateTime(getXsdDate());
        domain.setAuthor(getAuthor());
        domain.setName(domainName);
        domain.setShortName("Domain" + domainCount);
        domain.setVersion("v" + RND.nextInt(10));
        domain.setNote("Note " + domainName);

        //словарь вертикальных реквизитов и группы
        int maxVerticalProperties = getRandomNumberBetween(MIN_VERTICAL_PROPERTIES, MAX_VERTICAL_PROPERTIES);
        VerticalProperties properties = domain.getVerticalProperties();
        if (properties == null) {
            properties = new VerticalProperties();
        }
        //формируем словарь реквизитов
        List<VerticalProperty> verticalProperties = properties.getVerticalProperty();
        for (int count = 0; count < maxVerticalProperties; count++) {
            VerticalProperty property = createRandomProperty();
            if (property != null) {
                verticalProperties.add(property);
            }
        }
        domain.setVerticalProperties(properties);
        //нагенерируем группы
        int maxVerticalGroups = getRandomNumberBetween(MIN_VERTICAL_GROUPS, MAX_VERTICAL_GROUPS);
        List<VerticalPropertiesGroup> verticalPropertiesGroup = domain.getVerticalPropertiesGroup();
        for (int count = 0; count < maxVerticalGroups; count++) {
            VerticalPropertiesGroup group = createRandomGroup(verticalProperties);
            if (group != null) {
                verticalPropertiesGroup.add(group);
            }
        }

        ///кноты (knots)
        int maxKnots = getRandomNumberBetween(MIN_KNOTS, MAX_KNOTS);
        List<Knot> knots = domain.getKnot();
        for (int count = 0; count < maxKnots; count++) {
            Knot knot = createRandomKnot();
            knots.add(knot);
        }

        //анкоры (anchors)
        int maxAnchors = getRandomNumberBetween(MIN_ANCHORS, MAX_ANCHORS);
        List<Anchor> anchors = domain.getAnchor();
        for (int count = 0; count < maxAnchors; count++) {
            Anchor anchor = createRandomAnchor(knots);
            anchors.add(anchor);
        }

        //cross-domain anchors
        int maxCdAnchors = getRandomNumberBetween(MIN_ANCHORS, MAX_ANCHORS);
        List<CdAnchor> cdAnchors = domain.getCdAnchor();
        for (int count = 0; count < maxCdAnchors; count++) {
            domain.getCdAnchor().add(createRandomCdAnchor(knots));
        }

        //ties
        int maxTies = getRandomNumberBetween(MIN_ANCHORS, MAX_ANCHORS);
        for (int count = 0; count < maxTies; count++) {
            Tie tie = createRandomTie(anchors, cdAnchors, knots);
            domain.getTie().add(tie);
        }

        //tx anchors
        int maxTxAnchors = getRandomNumberBetween(MIN_ANCHORS, MAX_ANCHORS);
        for (int count = 0; count < maxTxAnchors; count++) {
            domain.getTxAnchor().add(createRandomTxAnchor(anchors));
        }

        //Area
        int maxAreas = getRandomNumberBetween(MIN_ANCHORS, MAX_ANCHORS);
        for (int count = 0; count < maxAreas; count++) {
            domain.getArea().add(createRandomArea(anchors));
        }

        return domain;
    }

    private VerticalProperty createRandomProperty() {
        VerticalProperty property = new VerticalProperty();
        property.setId(RND.nextLong());
        property.setName(createRandomDescriptor());
        property.setDisplayName(createRandomDescription());
        property.setDataRange(randomDataRange());
        ///knotRange пропускаем. ну его...
        return property;
    }

    private VerticalPropertiesGroup createRandomGroup(List<VerticalProperty> verticalProperties) {
        ///без вложенных групп
        VerticalPropertiesGroup group = new VerticalPropertiesGroup();
        group.setId(RND.nextLong());
        group.setUid(UUID.randomUUID().toString());
        group.setName(createRandomDescriptor());
        List<VerticalPropertyId> verticalProperty = group.getVerticalProperty();
        for (int i = 0; i < 3; i++) {
            //3 вертикальных реквизита достаточно.
            VerticalPropertyId verticalPropertyId = new VerticalPropertyId();
            verticalPropertyId.setId(verticalProperties.get(RND.nextInt(verticalProperties.size() - 1)).getId());
            verticalProperty.add(verticalPropertyId);
        }
        return group;
    }

    private TxAnchor createRandomTxAnchor(List<Anchor> anchors) {
        TxAnchor txAnchor = new TxAnchor();
        txAnchor.setUid(UUID.randomUUID().toString());

        int maxAnchors = getRandomNumberBetween(MIN_ANCHORS, MIN_ANCHORS + 1);
        for (int count = 0; count < maxAnchors; count++) {
            AnchorRole role = new AnchorRole();
            role.setDescription(createRandomDescription());
            role.setType(anchors.get(RND.nextInt(anchors.size())).getMnemonic());
            role.setRole(randomAlphabetic(3));
            role.setIdentifier(RND.nextBoolean() & RND.nextBoolean());
            txAnchor.getAnchorRole().add(role);
        }
        txAnchor.setIdentity(Identity.BIGINT);
        txAnchor.setDescriptor(createRandomDescriptor());
        txAnchor.setMnemonic(createRandomMnemonic(2));
        txAnchor.setDescription(createRandomDescription());
        return txAnchor;
    }

    private Area createRandomArea(List<Anchor> anchors) {
        Area area = new Area();
        area.setUid(UUID.randomUUID().toString());

        int maxArea = getRandomNumberBetween(MIN_ANCHORS, MIN_ANCHORS + 1);
        for (int count = 0; count < maxArea; count++) {
            AnchorRole role = new AnchorRole();
            role.setDescription(createRandomDescription());
            role.setType(anchors.get(RND.nextInt(anchors.size())).getMnemonic());
            role.setRole(randomAlphabetic(3));
            role.setIdentifier(RND.nextBoolean() & RND.nextBoolean());
            area.getAnchorRole().add(role);
        }
        area.setDescription(createRandomDescription());
        area.setColor("123456");
//        area.setSense(createRandomStringLabel());
        return area;
    }

    private Tie createRandomTie(List<Anchor> anchors, List<CdAnchor> cdAnchors, List<Knot> knots) {
        Tie tie = new Tie();
        tie.setUid(UUID.randomUUID().toString());
        if (RND.nextFloat() * RND.nextFloat() <= .25) {
            ///историчность
            tie.setTimeRange(IDENTITY);
        }
        if (RND.nextFloat() * RND.nextFloat() <= .25) {
            //кнотированный или нет
            KnotRole role = new KnotRole();
            role.setDescription(createRandomDescription());
            role.setType(knots.get(RND.nextInt(knots.size())).getMnemonic());
            role.setRole(randomAlphabetic(3));
            role.setIdentifier(RND.nextBoolean() & RND.nextBoolean());
            tie.setKnotRole(role);
        }
        int maxAnchors = getRandomNumberBetween(MIN_ANCHORS, MIN_ANCHORS + 1);
        for (int count = 0; count < maxAnchors; count++) {
            AnchorRole role = new AnchorRole();
            role.setDescription(createRandomDescription());
            if (RND.nextFloat() * RND.nextFloat() <= .25) {
                role.setType(cdAnchors.get(RND.nextInt(cdAnchors.size())).getMnemonic());
            } else {
                role.setType(anchors.get(RND.nextInt(anchors.size())).getMnemonic());
            }
            role.setRole(randomAlphabetic(3));
            role.setIdentifier(RND.nextBoolean() & RND.nextBoolean());
            tie.getAnchorRole().add(role);
        }

        //внутренние колонки tie.
        int columns = getRandomNumberBetween(MIN_TIE_COLUMNS, MAX_TIE_COLUMNS);
        for (int i = 0; i < columns; i++) {
            tie.getExtendedColumn().add(createRandomColumn(knots));
        }

        tie.setDescription(createRandomDescription());
        tie.setDescriptor(createRandomDescriptor());
        return tie;
    }

    private Knot createRandomKnot() {
        Knot knot = new Knot();
        knot.setLayout(createRandomLayout());
        knot.setMnemonic(createRandomMnemonic(KNOT_ATTR_MNEMONIC_LENGTH));
        knot.setDescriptor(createRandomDescriptor());
        knot.setDescription(createRandomDescription());
        knot.setLength(RND.nextInt(500));
        knot.setIdentity(IDENTITY);
        knot.setUid(UUID.randomUUID().toString());

        Values values = new Values();

        long maxValues = getRandomNumberBetween(MIN_KNOT_VALUES, MAX_KNOT_VALUES);
        for (long count = 1; count <= maxValues; count++) {
            Value value = new Value();
            value.setId(count);
            value.setValue(createRandomDescriptor());
            values.getValue().add(value);
        }
        knot.setValues(values);

        return knot;
    }

    private CdAnchor createRandomCdAnchor(List<Knot> knots) {
        CdAnchor cdAnchor = new CdAnchor();
        cdAnchor.setLayout(createRandomLayout());
        cdAnchor.setMnemonic(createRandomMnemonic(ANCHOR_MNEMONIC_LENGTH));
        cdAnchor.setDescriptor(createRandomDescriptor());
        cdAnchor.setDescription(createRandomDescription());
        cdAnchor.setIdentity(IDENTITY);
        cdAnchor.setUid(UUID.randomUUID().toString());

        //внутренние колонки таблицы кросс-доменного анкора.
        int columns = getRandomNumberBetween(MIN_ANCHOR_COLUMNS, MAX_ANCHOR_COLUMNS);
        for (int i = 0; i < columns; i++) {
            cdAnchor.getExtendedColumn().add(createRandomColumn(Collections.emptyList()));
        }

        //атрибуты кросс-доменного анкора
        int attributes = getRandomNumberBetween(MIN_ATTRIBUTES, MAX_ATTRIBUTES);
        for (int i = 0; i < attributes; i++) {
            cdAnchor.getAttribute().add(createRandomAttribute(knots));
        }

        return cdAnchor;
    }

    private Anchor createRandomAnchor(List<Knot> knots) {
        Anchor anchor = new Anchor();
        anchor.setLayout(createRandomLayout());
        anchor.setMnemonic(createRandomMnemonic(ANCHOR_MNEMONIC_LENGTH));
        anchor.setDescriptor(createRandomDescriptor());
        anchor.setDescription(createRandomDescription());
        anchor.setIdentity(IDENTITY);
        anchor.setUid(UUID.randomUUID().toString());

        //группы вертикальных реквизитов.

        //внутренние колонки таблицы анкора.
        int columns = getRandomNumberBetween(MIN_ANCHOR_COLUMNS, MAX_ANCHOR_COLUMNS);
        for (int i = 0; i < columns; i++) {
            anchor.getExtendedColumn().add(createRandomColumn(knots));
        }

        //атрибуты анкора
        int attributes = getRandomNumberBetween(MIN_ATTRIBUTES, MAX_ATTRIBUTES);
        for (int i = 0; i < attributes; i++) {
            anchor.getAttribute().add(createRandomAttribute(knots));
        }

        return anchor;
    }

    private Attribute createRandomAttribute(List<Knot> knots) {

        Attribute attribute = new Attribute();
        attribute.setLayout(createRandomLayout());
        attribute.setMnemonic(createRandomMnemonic(KNOT_ATTR_MNEMONIC_LENGTH));
        attribute.setDescriptor(createRandomDescriptor());
        attribute.setDescription(createRandomDescription());
        attribute.setUid(UUID.randomUUID().toString());

        //внутренние колонки таблицы атрибута
        int columns = getRandomNumberBetween(MIN_ATTR_COLUMNS, MAX_ATTR_COLUMNS);
        for (int i = 0; i < columns; i++) {
            attribute.getExtendedColumn().add(createRandomColumn(knots));
        }

        if (RND.nextFloat() * RND.nextFloat() <= .25) {
            ///историчность
            attribute.setTimeRange(IDENTITY);
        }
        if (RND.nextFloat() * RND.nextFloat() <= .25) {
            ///кнотированный атрибут
            Knot knot = knots.get(RND.nextInt(knots.size()));
            attribute.setKnotRange(knot.getMnemonic());
        } else {
            //не кнотированный, значит dataRange совпадает с identity кнота
            DataRange dataRange = randomDataRange();
            attribute.setDataRange(dataRange);
            if (dataRange == STRING) {
                attribute.setLength(RND.nextInt(500));
            }
        }

        return attribute;
    }

    private String createRandomMnemonic(int size) {
        return randomAlphabetic(size).toUpperCase();
    }

    private String createRandomDescriptor() {
        return randomAlphabetic(getRandomNumberBetween(MIN_DESCRIPTOR_LENGTH, MAX_DESCRIPTOR_LENGTH)).toLowerCase();
    }

    private String createRandomDescription() {
        int wordsCount = RND.nextInt(10) + 5;
        List<String> sentence = new ArrayList<>();
        for (int i = 0; i < wordsCount; i++) {
            sentence.add(words[RND.nextInt(words.length)]);
        }
        return StringUtils.capitalize(String.join(" ", sentence).toLowerCase() + '.');
    }

    public static String getAuthor() {
        return AUTHORS[RND.nextInt(AUTHORS.length)];
    }

    public static XMLGregorianCalendar getXsdDate() {
        return FACTORY.newXMLGregorianCalendar(LocalDateTime.now(ZONE_ID).toString());
    }

    private DataRange randomDataRange() {
        return DATA_RANGES[RND.nextInt(DBT_LENGTH)];
    }

    private Layout createRandomLayout() {
        Layout layout = new Layout();
        layout.setX(RND.nextDouble() * 1000);
        layout.setY(RND.nextDouble() * 1000);
        return layout;
    }

    private ExtendedColumn createRandomColumn(List<Knot> knots) {
        ExtendedColumn extendedColumn = new ExtendedColumn();
        extendedColumn.setColumnName(createRandomDescriptor());
        extendedColumn.setUid(UUID.randomUUID().toString());
        if (!knots.isEmpty() && RND.nextFloat() * RND.nextFloat() <= .25) {
            //колонка кнотированная, тип данных определяется identity кнота
            extendedColumn.setKnotRange(knots.get(RND.nextInt(knots.size())).getMnemonic());
        } else {
            extendedColumn.setDataRange(randomDataRange());
        }
        return extendedColumn;
    }

    public static int getRandomNumberBetween(int left, int right) {
        return left + RND.nextInt(right - left + 1);
    }

    /**
     * Генерация элемента domain со случайным содержимым
     * для модели деплоя. domain — элемент, содержащий
     * в себе элементы deployItem, состоящих в одном домене.
     *
     * @return domain — заполненный элемент.
     */
    private org.leandi.schema.deploy.Domain createRandomDomainForDeploy() {
        org.leandi.schema.deploy.Domain domain = new org.leandi.schema.deploy.Domain();
        domain.setShortName(createRandomStringLabel());
        int maxItems = getRandomNumberBetween(MIN_ITEMS, MAX_ITEMS);
        for (int count = 0; count < maxItems; count++) {
            DeployItem deployItem = createRandomDeployItem();
            domain.getItem().add(deployItem);
        }
        return domain;
    }

    /**
     * Генерация элемента deployItem со случайным содержимым
     * для модели деплоя. deployItem — описание
     * способа развертывания/хранения данных.
     *
     * @return deployItem — заполненный элемент.
     */
    private DeployItem createRandomDeployItem() {
        DeployItem deployItem = new DeployItem();
        deployItem.setFqn(createRandomStringLabel());
        return deployItem;
    }

    private DbHost createRandomDbHost() {
        DbHost dbHost = new DbHost();
        dbHost.setHost(createRandomStringLabel());
        dbHost.setUserName(createRandomStringLabel());
        dbHost.setPort(Integer.toString(randomPort()));
        dbHost.setDbName(createRandomStringLabel());
        dbHost.setDbType(randomDbType());
        dbHost.setClusterId(Integer.toString(getRandomNumberBetween(MIN_CLUSTER_ID, MAX_CLUSTER_ID)));
        dbHost.setHostName(createRandomStringLabel());
        int maxDomains = getRandomNumberBetween(MIN_DOMAINS, MAX_DOMAINS);
        for (int count = 0; count < maxDomains; count++) {
            dbHost.getDomain().add(createRandomDomainForDeploy());
        }

        return dbHost;
    }

    private FsHost createRandomFsHost() {
        FsHost fsHost = new FsHost();
        fsHost.setHost(createRandomStringLabel());
        fsHost.setUserName(createRandomStringLabel());
        fsHost.setFolder(createRandomStringLabel());
        fsHost.setClusterId(Integer.toString(getRandomNumberBetween(MIN_CLUSTER_ID, MAX_CLUSTER_ID)));
        fsHost.setHostName(createRandomStringLabel());
        int maxDomains = getRandomNumberBetween(MIN_DOMAINS, MAX_DOMAINS);
        for (int count = 0; count < maxDomains; count++) {
            fsHost.getDomain().add(createRandomDomainForDeploy());
        }

        return fsHost;
    }

    private DbTypeType randomDbType() {
        return DB_TYPES[RND.nextInt(DB_TYPES.length)];
    }

    private String createRandomStringLabel() {
        return randomAlphabetic(getRandomNumberBetween(MIN_LABEL_LENGTH, MAX_LABEL_LENGTH)).toLowerCase();
    }

    private int randomPort() {
        return getRandomNumberBetween(MIN_PORT, MAX_PORT);
    }

}
