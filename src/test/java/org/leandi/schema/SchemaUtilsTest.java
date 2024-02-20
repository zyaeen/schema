package org.leandi.schema;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.*;
import org.leandi.schema.deploy.*;
import org.leandi.schema.domain.*;
import org.leandi.schema.domain.Domain;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты на расстановку UID в узлах и их элементах (Extended Column).
 * Проверки на: существование проекта; существование доменов в проекте;
 * корректное создание проекта (без домена в составе конструктора);
 * корректную инициализацию домена (без проекта в составе конструктора);
 */
class SchemaUtilsTest {

    private static SchemaUtils domainUtils;
    private static SchemaUtils projectUtils;
    private static SchemaUtils deployUtils;

    @BeforeEach
    public void init() {
        domainUtils = SchemaUtils.builder()
                .domainXml(SchemaUtilsTest.class.getResourceAsStream("/domain.xml"))
                .build();
        projectUtils = SchemaUtils.builder()
                .projectXml(SchemaUtilsTest.class.getResourceAsStream("/project.xml"))
                .build();
        deployUtils = SchemaUtils.builder()
                .deployXml(SchemaUtilsTest.class.getResourceAsStream("/deploy.xml"))
                .build();
    }

    @Test
    @DisplayName("Checking if all nodes of domain have an UID")
    void validateDomainUid() {

        // Проверка на то, что uid был проставлен у анкеров,
        // tx-анкеров, таев, кнотов, вложенных extendedColumn
        domainUtils.generateDomainUid();
        Domain domain = domainUtils.getDomain();
        assertFalse(StringUtils.isEmpty(domain.getUid()));
        assertTrue(checkAnchorsForUid(domain.getAnchor()), "Some anchors elements don't have uid");
        assertTrue(checkTxAnchorsForUid(domain.getTxAnchor()), "Some txAnchors elements don't have uid");
        assertTrue(checkTiesForUid(domain.getTie()), "Some ties elements don't have uid");
        assertTrue(checkKnotsForUid(domain.getKnot()), "Some knots elements don't have uid");

        // todo:
        //  Закомментировано, т.к. в конструкторе при отсутствии domainXml и project сгенерируются необогащенные объекты домена и проекта.
        //  Будет рассматриваться и пересматриваться.
        // Проверка на отсутствие project в составе domainUtils
        // assertThrows(IllegalArgumentException.class, () -> domainUtils.generateProjectUid());
    }

    @Test
    @DisplayName("Checking if all nodes of deploy scheme have an UID")
    void validateDeployUid() {
        // Проверка на то, что uid был проставлен у db и fs-хостов.
        deployUtils.generateDeployUid();
        Deploy deploy = deployUtils.getDeploy();
        assertTrue(checkDbHostsForUid(deploy.getDbHost()), "Some db-hosts elements don't have uid");
        assertTrue(checkFsHostsForUid(deploy.getFsHost()), "Some fs-hosts elements don't have uid");
    }


    @Test
    @DisplayName("Checking if all project's domains have null elements")
    void validateProjectUid() {

        projectUtils.generateProjectUid();
        Project project = projectUtils.getProject();

        // Проверка на то, что в проект не пустой и в нем есть домены.
        assertNotNull(project, "Project is empty!");
        assertNotEquals(0, project.getDomain().size(), "Project doesn't have a domains!");

        // Проверка на то, что в проекте нет пустых доменов,
        // и в каждом домене проставлены uid для узлов.
        boolean doDomainsHaveUids = true;
        boolean isHereEmptyDomains = false;
        for (Domain domain : project.getDomain()) {
            if (domain != null) {
                if (!(checkAnchorsForUid(domain.getAnchor()) && checkTiesForUid(domain.getTie())
                        && checkKnotsForUid(domain.getKnot()) && checkTxAnchorsForUid(domain.getTxAnchor()))) {
                    doDomainsHaveUids = false;
                }
            } else {
                isHereEmptyDomains = true;
            }
        }

        assertFalse(isHereEmptyDomains, "Project has null domains!");
        assertTrue(doDomainsHaveUids, "Project domains have components with null uid");

        // todo:
        //  Закомментировано, т.к. в конструкторе при отсутствии domainXml и project сгенерируются необогащенные объекты домена и проекта.
        //  Будет рассматриваться и пересматриваться.
        // Проверка на отсутствие domain в составе projectUtils
//        assertThrows(IllegalArgumentException.class, () -> projectUtils.generateDomainUid());
    }

    @Test
    @DisplayName("Checking node deleting")
    void deleteNodeByUid() {
        String anchorUid1 = "anc7ee96-aa95-444c-b23a-2ac890986f0c";
        String anchorMne1 = "TU";

        String anchorUid2 = "anc2ee96-aa95-444c-b23a-2ac890986f0c";
        String anchorMne2 = "LL";

        String txAnchorUid1 = "txa7ee96-aa95-444c-b23a-2ac890986f0c";
        String txAnchorMne1 = "TX1";

        String txAnchorUid2 = "txa2ee96-aa95-444c-b23a-2ac890986f0c";
        String txAnchorMne2 = "TX2";

        String knotUid = "knotee96-aa95-444c-b23a-2ac890986f0c";
        String knotMne = "EEK";

        String connexionUid = "ff780214-03f4-4941-bd33-12dd43941123";

        assertNotEquals(-1, domainUtils.lookUpAnchorIndexByMnemonic(anchorMne1));
        domainUtils.deleteAnchor(anchorUid1);
        assertEquals(-1, domainUtils.lookUpAnchorIndexByMnemonic(anchorMne1));

        assertNotEquals(-1, domainUtils.lookUpAnchorIndexByMnemonic(anchorMne2));
        domainUtils.deleteAnchor(anchorUid2);
        assertEquals(-1, domainUtils.lookUpAnchorIndexByMnemonic(anchorMne2));

        assertNotEquals(-1, domainUtils.lookUpTxAnchorIndexByMnemonic(txAnchorMne1));
        domainUtils.deleteTxAnchor(txAnchorUid1);
        assertEquals(-1, domainUtils.lookUpTxAnchorIndexByMnemonic(txAnchorMne1));

        assertNotEquals(-1, domainUtils.lookUpTxAnchorIndexByMnemonic(txAnchorMne2));
        domainUtils.deleteTxAnchor(txAnchorUid2);
        assertEquals(-1, domainUtils.lookUpTxAnchorIndexByMnemonic(txAnchorMne2));

        assertNotEquals(-1, domainUtils.lookUpKnotIndexByMnemonic(knotMne));
        domainUtils.deleteKnot(knotUid);
        assertEquals(-1, domainUtils.lookUpKnotIndexByMnemonic(knotMne));

        assertEquals(1, projectUtils.getProject().getConnexions().size());
        projectUtils.deleteConnexion(connexionUid);
        assertEquals(0, projectUtils.getProject().getConnexions().size());
    }

    @Test
    @DisplayName("Checking domain node deleting from project")
    void deleteDomainByShortName() {
        String domainShortName = "Domain0";

        assertNotNull(projectUtils.getProject()
                .getDomain()
                .stream().filter(domain -> domain.getShortName().equals(domainShortName)).findAny().orElse(null));
        projectUtils.deleteDomain(domainShortName);
        assertNull(projectUtils.getProject()
                .getDomain()
                .stream().filter(domain -> domain.getShortName().equals(domainShortName)).findAny().orElse(null));
    }

    @Test
    @DisplayName("Checking domain node deleting from deploy")
    void deleteDomainByShortNameFromDeploy() {
        String domainShortName = "Domain1";
        assertNotNull(checkDomainInDeploy(domainShortName));
        deployUtils.deleteDomainFromDeploy(domainShortName);
        assertNull(checkDomainInDeploy(domainShortName));
    }

    private org.leandi.schema.deploy.Domain checkDomainInDeploy(String shortName) {
        Deploy deploy = deployUtils.getDeploy();
        org.leandi.schema.deploy.Domain dom = null;
        for (DbHost dbHost : deploy.getDbHost()) {
            dom = getDomainByShortName(shortName, dbHost);
        }
        for (FsHost fsHost : deploy.getFsHost()) {
            dom = getDomainByShortName(shortName, fsHost);
        }
        return dom;
    }

    private org.leandi.schema.deploy.Domain getDomainByShortName(String shortName, HostInfo hostInfo) {
        return hostInfo.getDomain().stream()
                .filter(domain -> StringUtils.equals(shortName, domain.getShortName()))
                .findAny().orElse(null);
    }

    @Test
    @DisplayName("Checking group node deleting")
    void deleteNodesByUids() {
        String anchorsUids = "anc7ee96-aa95-444c-b23a-2ac890986f0c,anc2ee96-aa95-444c-b23a-2ac890986f0c";
        String anchorMne1 = "TU";
        String anchorMne2 = "LL";

        assertNotEquals(-1, domainUtils.lookUpAnchorIndexByMnemonic(anchorMne1));
        assertNotEquals(-1, domainUtils.lookUpAnchorIndexByMnemonic(anchorMne2));
        domainUtils.deleteAnchor(anchorsUids);
        assertEquals(-1, domainUtils.lookUpAnchorIndexByMnemonic(anchorMne1));
        assertEquals(-1, domainUtils.lookUpAnchorIndexByMnemonic(anchorMne2));
    }

    @Test
    @DisplayName("Checking correct fill domain elements by another domain data")
    void fillDomain() {
        SchemaUtils domain = SchemaUtils.builder().build();
        domain.fillDomainByAnotherDomain(domainUtils);
        assertEquals(domain.getDomain().getAnchor(), domainUtils.getDomain().getAnchor());
        assertEquals(domain.getDomain().getTxAnchor(), domainUtils.getDomain().getTxAnchor());
        assertEquals(domain.getDomain().getKnot(), domainUtils.getDomain().getKnot());
        assertEquals(domain.getDomain().getTie(), domainUtils.getDomain().getTie());
//        assertEquals(domain.getDomain().getVerticalProperties().getVerticalProperty(), domainUtils.getDomain().getVerticalProperties().getVerticalProperty());
        assertEquals(domain.getDomain().getArea(), domainUtils.getDomain().getArea());
        assertEquals(domain.getDomain().getCdAnchor(), domainUtils.getDomain().getCdAnchor());
//        assertEquals(domain.getDomain().getVerticalPropertiesGroup(), domainUtils.getDomain().getVerticalPropertiesGroup());
    }

    @Test
    @DisplayName("Checking adding a new item to domain")
    void addItem() {
        String deployItemString = StringUtils.EMPTY +
                "[\n" +
                "  {\n" +
                "    \"shortName\": \"Domain2\",\n" +
                "    \"host\": \"vdspqfjta\",\n" +
                "    \"userName\": \"qhiue\",\n" +
                "    \"dbType\": \"POSTGRES\",\n" +
                "    \"port\": \"1580\",\n" +
                "    \"dbName\": \"ycbfzpbur\",\n" +
                "    \"fqn\": \"SK\"\n" +
                "  }\n" +
                "]";

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode[] nodes = null;
        try {
            nodes = mapper.readValue(deployItemString, ObjectNode[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось распарсить список узлов из строкового представления.");
        }

        ObjectMapper objectMapper = new ObjectMapper();
        SchemaUtils.Item[] items = objectMapper.convertValue(nodes, SchemaUtils.Item[].class);
        SchemaUtils.Item item = items[0];

        assertFalse(checkItemExists(deployUtils.getDeploy(), item));
        deployUtils.addDeployItem(deployItemString);
        assertTrue(checkItemExists(deployUtils.getDeploy(), item));
    }

    @Test
    @DisplayName("Checking deleting an item of deploy schema")
    void deleteItem() {
        String deployItemString = StringUtils.EMPTY +
                "[\n" +
                "  {\n" +
                "    \"shortName\": \"Domain2\",\n" +
                "    \"host\": \"lfgqwk\",\n" +
                "    \"userName\": \"ohjfpkj\",\n" +
                "    \"dbType\": \"HSQLDB\",\n" +
                "    \"port\": \"8125\",\n" +
                "    \"dbName\": \"sivbnrv\",\n" +
                "    \"fqn\": \"SK\"\n" +
                "  }\n" +
                "]";

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode[] nodes = null;
        try {
            nodes = mapper.readValue(deployItemString, ObjectNode[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось распарсить список узлов из строкового представления.");
        }

        ObjectMapper objectMapper = new ObjectMapper();
        SchemaUtils.Item[] items = objectMapper.convertValue(nodes, SchemaUtils.Item[].class);
        SchemaUtils.Item item = items[0];

        assertTrue(checkItemExists(deployUtils.getDeploy(), item));
        deployUtils.deleteDeployItem(deployItemString);
        assertFalse(checkItemExists(deployUtils.getDeploy(), item));
    }

    boolean checkItemExists(Deploy deploy, SchemaUtils.Item item) {
        for (DbHost dbHost : deploy.getDbHost()) {
            for (org.leandi.schema.deploy.Domain domain : dbHost.getDomain()) {
                if (StringUtils.equals(domain.getShortName(), item.getShortName())) {
                    for (DeployItem deployItem : domain.getItem()) {
                        if (StringUtils.equals(dbHost.getDbName(), item.getDbName())
                                && StringUtils.equals(dbHost.getHost(), item.getHost())
                                && StringUtils.equals(dbHost.getUserName(), item.getUserName())
                                && dbHost.getDbType() == item.getDbType()
                                && StringUtils.equals(dbHost.getPort(), item.getPort())
                                && StringUtils.equals(deployItem.getFqn(), item.getFqn())) {
                            return true;
                        }
                    }
                }
            }
        }
        for (FsHost fsHost : deploy.getFsHost()) {
            for (org.leandi.schema.deploy.Domain domain : fsHost.getDomain()) {
                if (StringUtils.equals(domain.getShortName(), item.getShortName())) {
                    for (DeployItem deployItem : domain.getItem()) {
                        if (StringUtils.equals(fsHost.getHost(), item.getHost())
                                && StringUtils.equals(fsHost.getUserName(), item.getUserName())
                                && StringUtils.equals(deployItem.getFqn(), item.getFqn())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * @param objectInfo - объект, для которого проверяется наличие uid
     * @return {@code true/false} - в uid проставлен / не проставлен.
     */
    boolean doesNotHaveUid(ObjectInfo objectInfo) {
        return objectInfo.getUid() == null || StringUtils.equals(objectInfo.getUid(), StringUtils.EMPTY);
    }

    /**
     * @param extendedColumn - объект, для которого проверяется наличие uid
     * @return {@code true/false} - в uid проставлен / не проставлен.
     */
    boolean doesNotHaveUid(ExtendedColumn extendedColumn) {
        return extendedColumn.getUid() == null || StringUtils.equals(extendedColumn.getUid(), StringUtils.EMPTY);
    }
    
    /**
     * @param hostInfo - объект, для которого проверяется наличие uid
     * @return {@code true/false} - в uid проставлен / не проставлен.
     */
    boolean doesNotHaveUid(HostInfo hostInfo) {
        return hostInfo.getUid() == null || StringUtils.equals(hostInfo.getUid(), StringUtils.EMPTY);
    }

    /**
     * @param hosts - список хостов, проверяемых на наличие uid.
     * @return {@code true/false} - в uid проставлен / не проставлен.
     */
    boolean checkDbHostsForUid(List<DbHost> hosts) {
        for (DbHost host : hosts) {
            if (doesNotHaveUid(host)) {
                return false;
            }
        }
        return true;
    }
    /**
     * @param hosts - список хостов, проверяемых на наличие uid.
     * @return {@code true/false} - в uid проставлен / не проставлен.
     */
    boolean checkFsHostsForUid(List<FsHost> hosts) {
        for (FsHost host : hosts) {
            if (doesNotHaveUid(host)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param anchors - список анкеров, проверяемых на наличие uid.
     *                Также проверяются связанные с анкером атрибуты
     *                и extendedColumn.
     * @return {@code true/false} - в uid проставлен / не проставлен.
     */
    boolean checkAnchorsForUid(List<Anchor> anchors) {
        for (Anchor anchor : anchors) {
            for (ExtendedColumn extendedColumn : anchor.getExtendedColumn()) {
                if (doesNotHaveUid(extendedColumn)) {
                    return false;
                }
            }
            if (doesNotHaveUid(anchor)) {
                return false;
            }
            for (Attribute attribute : anchor.getAttribute()) {
                if (doesNotHaveUid(attribute)) {
                    return false;
                }
                for (ExtendedColumn extendedColumn : attribute.getExtendedColumn()) {
                    if (doesNotHaveUid(extendedColumn)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * @param ties - список таев, проверяемых на наличие uid.
     *             Также проверяются вложенные в тай extendedColumn.
     * @return {@code true/false} - в uid проставлен / не проставлен.
     */
    boolean checkTiesForUid(List<Tie> ties) {
        for (Tie tie : ties) {
            if (doesNotHaveUid(tie)) {
                return false;
            }
            for (ExtendedColumn extendedColumn : tie.getExtendedColumn()) {
                if (doesNotHaveUid(extendedColumn)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * @param knots - список таев, проверяемых на наличие uid.
     * @return {@code true/false} - в uid проставлен / не проставлен.
     */
    boolean checkKnotsForUid(List<Knot> knots) {
        for (Knot knot : knots) {
            if (doesNotHaveUid(knot)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param txAnchors - список tx-анкеров, проверяемых на наличие uid.
     *                  Также проверяются связанные с tx-анкером атрибуты
     *                  и extendedColumn.
     * @return {@code true/false} - в uid проставлен / не проставлен.
     */
    boolean checkTxAnchorsForUid(List<TxAnchor> txAnchors) {
        for (TxAnchor anchor : txAnchors) {
            for (ExtendedColumn extendedColumn : anchor.getExtendedColumn()) {
                if (doesNotHaveUid(extendedColumn)) {
                    return false;
                }
            }
            if (doesNotHaveUid(anchor)) {
                return false;
            }
            for (Attribute attribute : anchor.getAttribute()) {
                if (doesNotHaveUid(attribute)) {
                    return false;
                }
                for (ExtendedColumn extendedColumn : attribute.getExtendedColumn()) {
                    if (doesNotHaveUid(extendedColumn)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}