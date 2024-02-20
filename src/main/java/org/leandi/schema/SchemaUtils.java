package org.leandi.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.xml.bind.*;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.leandi.schema.deploy.*;
import org.leandi.schema.domain.*;
import org.leandi.schema.domain.Domain;
import org.leandi.schema.domain.Properties;
import org.leandi.schema.domain.basetypes.IndexType;
import org.leandi.schema.domain.basetypes.Value;
import org.leandi.schema.domain.basetypes.Values;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Класс SchemaUtils для работы с анкерной XML-схемой
 * В конструкторе принимает
 *
 * @author Раяз Фаяз
 */
public class SchemaUtils {


    /**
     * Бизнес-домен — одна анкерная схема.
     */
    @Getter
    private Domain domain;

    /**
     * Проект — группа бизнес-доменов.
     */
    @Getter
    private Project project;

    /**
     * Deploy — модель деплоя.
     */
    @Getter
    private Deploy deploy;

    /**
     * XML-схема файла проекта
     */
    InputStream projectXml;
    /**
     * XML-схема файла домена.
     */
    InputStream domainXml;

    /**
     * XML-схема файла деплоя.
     */
    InputStream deployXml;

    /**
     * Список актуальных групп динамических реквизитов.
     */
    private List<Group> actualGroups = new ArrayList<>();

    @Builder
    public SchemaUtils(InputStream domainXml, InputStream projectXml, InputStream deployXml,
                       Domain domain, Project project, Deploy deploy) {
        this.domainXml = domainXml;
        this.projectXml = projectXml;
        this.deployXml = deployXml;

        try {
            // Сделано так, чтобы была возможность работать с необогащенными структурами
            // с целью их обогащения уже в самом моделере.
            this.domain = new Domain();
            this.project = new Project();
            this.deploy = new Deploy();

            JAXBContext jaxbContext = JAXBContext.newInstance(Project.class, Domain.class, Deploy.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

            if (domainXml != null) {
                JAXBElement<Domain> e = unmarshaller.unmarshal(new StreamSource(domainXml), Domain.class);
                this.domain = e.getValue();
            }
            if (projectXml != null) {
                JAXBElement<Project> e = unmarshaller.unmarshal(new StreamSource(projectXml), Project.class);
                this.project = e.getValue();
            }
            if (deployXml != null) {
                JAXBElement<Deploy> e = unmarshaller.unmarshal(new StreamSource(deployXml), Deploy.class);
                this.deploy = e.getValue();
            }
        } catch (JAXBException e) {
            throw new IllegalArgumentException("Не удалось провести анмаршаллинг файла. Проверьте структуру XML.");
        }

        if (domain != null) {
            this.domain = domain;
        }
        if (project != null) {
            this.project = project;
        }
        if (deploy != null) {
            this.deploy = deploy;
        }
        generateProjectUid();
        generateDomainUid();
        generateDeployUid();
    }

    /**
     * Метод сбора аткуального списка групп.
     */
    private void aggregateGroups() {
        actualGroups.clear();
        List<Group> groups = new ArrayList<>();
        for (Group group : this.domain.getGroup()) {
            Group newGroup = new Group();
            newGroup.setId(group.getId());
            newGroup.setName(group.getName());
            newGroup.setDescription(group.getDescription());
            List<Group> groupsInGroup = new ArrayList<>();
            for (Group groupInGroup : group.getGroup()) {
                Group gr = this.domain.getGroup().stream()
                        .filter(g -> g.getId().equals(groupInGroup.getId())).findFirst().orElse(null);
                if (gr != null) {
                    List<Property> propertiesInGroup = aggregateProperties(gr);
                    gr.getProperty().clear();
                    gr.getProperty().addAll(propertiesInGroup);
                    groupsInGroup.add(gr);
                }

            }
            List<Property> propertiesInGroup = aggregateProperties(group);
            newGroup.getProperty().addAll(propertiesInGroup);
            newGroup.getGroup().addAll(groupsInGroup);
            groups.add(newGroup);
        }
        actualGroups.addAll(groups);
    }

    /**
     * Вспомгательный метод сбора метаданных реквизитов для группы.
     *
     * @param gr группа.
     * @return список насыщенных реквизитов.
     */
    private List<Property> aggregateProperties(Group gr) {
        List<Property> localPropertiesInGroup = new ArrayList<>();
        for (Property property : gr.getProperty()) {
            List<Properties> properties = this.domain.getProperties();
            if (!properties.isEmpty()) {
                localPropertiesInGroup.addAll(properties.get(0).getProperty().stream()
                        .filter(pr -> pr.getId().equals(property.getId())).collect(Collectors.toList()));
            }
        }
        return localPropertiesInGroup;
    }

    /**
     * Метод добавления новой группы.
     *
     * @param json json-представление новой/обновляемой группы
     */
    public void updateGroup(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Group group = mapper.readValue(json, Group.class);
            int index = lookUpGroupIndexById(group.getId());
            if (index == -1) {
                this.domain.getGroup().add(group);
            } else {
                this.domain.getGroup().get(index).setName(group.getName());
                this.domain.getGroup().get(index).setDescription(group.getDescription());
                System.out.println();
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Метод удаления группы.
     *
     * @param groupId идентификатор удаляемой группы.
     */
    public void removeGroup(String groupId) {
        for (Group group : this.domain.getGroup()) {
            removeGroup(groupId, group);
        }
        this.domain.getGroup().removeIf(group -> StringUtils.equals(groupId, group.getId()));
    }

    /**
     * Вспомогательный рекурсивный метод для удаления группы в составе родительских групп.
     *
     * @param groupId идентификатор удаляемой группы.
     * @param group   родительская группа.
     */
    private void removeGroup(String groupId, Group group) {
        group.getGroup().removeIf(gr -> StringUtils.equals(gr.getId(), groupId));
        for (Group gr : group.getGroup()) {
            removeGroup(groupId, gr);
        }
    }

    /**
     * Метод удаления реквизитов в составе группы.
     *
     * @param json структура, содержащая id группы и список id удаляемых реквизитов.
     */
    public void removePropertiesFromGroup(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            SerializeHelperClass item = mapper.readValue(json, SerializeHelperClass.class);
            Group group = lookUpGroupById(item.getId());
            if (group != null) {
                group.getProperty().removeIf(property -> item.getElements().contains(property.getId()));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Метод удаления групп в составе группы.
     *
     * @param json структура, содержащая id родительской группы и список id удаляемых групп.
     */
    public void removeGroupsFromGroup(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            SerializeHelperClass item = mapper.readValue(json, SerializeHelperClass.class);
            Group group = lookUpGroupById(item.getId());
            if (group != null) {
                group.getGroup().removeIf(gr -> item.getElements().contains(gr.getId()));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Добавление реквизитов в группу.
     *
     * @param json - структура, содержащая id группы и набор id добавляемых реквизитов.
     */
    public void addPropertiesToGroup(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            SerializeHelperClass item = mapper.readValue(json, SerializeHelperClass.class);
            Group group = lookUpGroupById(item.getId());
            int index = lookUpGroupIndexById(item.getId());
            if (group != null) {
                for (String propertyId : item.getElements()) {
                    if (group.getProperty().stream().noneMatch(pr -> StringUtils.equals(pr.getId(), propertyId))) {
                        Property property = new Property();
                        property.setId(propertyId);
                        group.getProperty().add(property);
                    }
                }
                this.domain.getGroup().set(index, group);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Добавление подгрупп в группу.
     *
     * @param json - структура, содержащая id группы и набор id добавляемых групп.
     */
    public void addGroupsToGroup(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            SerializeHelperClass item = mapper.readValue(json, SerializeHelperClass.class);
            Group group = lookUpGroupById(item.getId());
            if (group != null) {
                for (String groupId : item.getElements()) {
                    if (group.getGroup().stream().noneMatch(gr -> StringUtils.equals(gr.getId(), groupId))) {
                        Group groupToAdd = new Group();
                        groupToAdd.setId(groupId);
                        group.getGroup().add(groupToAdd);
                    }
                }
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Метод обновления реквизита.
     *
     * @param json json-представление реквизита.
     */
    public void updateProperty(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Property property = mapper.readValue(json, Property.class);
            int index = lookUpPropertyIndexById(property.getId());
            if (index == -1) {
                if (this.domain.getProperties().isEmpty()) {
                    Properties properties = new Properties();
                    this.domain.getProperties().add(properties);
                }
                this.domain.getProperties().get(0).getProperty().add(property);
            } else {
                this.domain.getProperties().get(0).getProperty().set(index, property);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Метод удаления реквизитов.
     *
     * @param json json-представление реквизита.
     */
    public void deleteProperty(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            List<String> propertyId = mapper.readValue(json, SerializeHelperClass.class).getElements();
            if (!this.domain.getProperties().isEmpty()) {
                this.domain.getProperties().get(0).getProperty().removeIf(property -> propertyId.contains(property.getId()));
            }
            this.domain.getGroup().forEach(group -> {
                group.getProperty().removeIf(property -> propertyId.contains(property.getId()));
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Расстановка uid для узлов и их extendedColumn в составе каждого домена проекта.
     */
    public void generateProjectUid() {
        if (project != null) {
            project.getDomain().forEach(this::generateDomainUid);
            if (StringUtils.isEmpty(project.getUid())) {
                project.setUid(UUID.randomUUID().toString());
            }
        } else {
            throw new IllegalArgumentException("Проект пуст!");
        }
    }

    /**
     * Расстановка uid для узлов и их extendedColumn в составе домена.
     */
    public void generateDomainUid() {
        if (domain != null) {
            generateDomainUid(domain);
        } else {
            throw new IllegalArgumentException("Домен пуст!");
        }
    }

    /**
     * Расстановка uid для хостов в составе схемы деплоя.
     */
    public void generateDeployUid() {
        if (deploy != null) {
            deploy.getFsHost().forEach(this::generateUid);
            deploy.getDbHost().forEach(this::generateUid);
        } else {
            throw new IllegalArgumentException("Схема деплоя пуста!");
        }
    }


    /**
     * Расстановка uid для узлов и их extendedColumn в составе конкретного домена.
     *
     * @param domain домен, для которого устанавливаются uid.
     *               Расстановка uid для узлов и их extendedColumn в составе domain.
     */
    public void generateDomainUid(Domain domain) {
        if (StringUtils.isEmpty(domain.getUid())) {
            domain.setUid(UUID.randomUUID().toString());
        }
        domain.getAnchor().forEach(anchor -> {
            generateUid(anchor);
            anchor.getExtendedColumn().forEach(this::generateUid);
            anchor.getAttribute().forEach(attribute -> {
                generateUid(attribute);
                attribute.getExtendedColumn().forEach(this::generateUid);
                if (attribute.getIndexes() != null) {
                    attribute.getIndexes().getIndex().forEach(this::generateUid);
                }
            });
            if (anchor.getIndexes() != null) {
                anchor.getIndexes().getIndex().forEach(this::generateUid);
            }
        });
        domain.getTie().forEach(tie -> {
            generateUid(tie);
            tie.getExtendedColumn().forEach(this::generateUid);
            if (tie.getIndexes() != null) {
                tie.getIndexes().getIndex().forEach(this::generateUid);
            }
        });
        domain.getKnot().forEach(
                knot -> {
                    this.generateUid(knot);
                    this.replaceItemToValue(knot);
                    if (knot.getValues() != null) {
                        knot.getValues().getValue().forEach(this::generateUid);
                    }
                }
        );
        domain.getTxAnchor().forEach(txAnchor -> {
            generateUid(txAnchor);
            txAnchor.getExtendedColumn().forEach(this::generateUid);
            txAnchor.getAttribute().forEach(attribute -> {
                generateUid(attribute);
                attribute.getExtendedColumn().forEach(this::generateUid);
                if (attribute.getIndexes() != null) {
                    attribute.getIndexes().getIndex().forEach(this::generateUid);
                }
            });
            if (txAnchor.getIndexes() != null) {
                txAnchor.getIndexes().getIndex().forEach(this::generateUid);
            }
        });
        domain.getCdAnchor().forEach(cdAnchor -> {
            generateUid(cdAnchor);
            cdAnchor.getExtendedColumn().forEach(this::generateUid);
            cdAnchor.getAttribute().forEach(attribute -> {
                generateUid(attribute);
                attribute.getExtendedColumn().forEach(this::generateUid);
                if (attribute.getIndexes() != null) {
                    attribute.getIndexes().getIndex().forEach(this::generateUid);
                }
            });
            if (cdAnchor.getIndexes() != null) {
                cdAnchor.getIndexes().getIndex().forEach(this::generateUid);
            }
        });
        domain.getArea().forEach(this::generateUid);
    }

    // Вспомогательный метод создания тега values в кноте вместо items.
    private void replaceItemToValue(Knot knot) {
        Values items = knot.getItems();
        if (items != null) {
            List<Value> item = items.getItem();
            Values values = new Values();
            if (!item.isEmpty()) {
                item.forEach(v -> {
                    Value val = new Value();
                    val.setValue(v.getValue());
                    val.setId(v.getId());
                    values.getValue().add(val);
                });
            }
            knot.setValues(values);
            knot.setItems(null);
        }
    }

    /**
     * Установка uid для конкретного узла или extendedColumn.
     *
     * @param knotValue значение кнота, для которого
     *                  генерируется uid.
     */
    private void generateUid(Value knotValue) {
        if (knotValue.getUid() == null) {
            knotValue.setUid(UUID.randomUUID().toString());
        }
    }

    /**
     * Установка uid для конкретного узла или extendedColumn.
     *
     * @param index индекс, для которого
     *              генерируется uid.
     */
    private void generateUid(IndexType index) {
        if (index.getUid() == null) {
            index.setUid(UUID.randomUUID().toString());
        }
    }

    /**
     * Установка uid для конкретного индекса.
     *
     * @param objectInfo узел, для которого
     *                   генерируется uid.
     */
    private void generateUid(ObjectInfo objectInfo) {
        if (objectInfo.getUid() == null) {
            objectInfo.setUid(UUID.randomUUID().toString());
        }
    }

    /**
     * Установка uid для конкретного индекса.
     *
     * @param extendedColumn ExtendedColumn, для которого
     *                   генерируется uid.
     */
    private void generateUid(ExtendedColumn extendedColumn) {
        if (extendedColumn.getUid() == null) {
            extendedColumn.setUid(UUID.randomUUID().toString());
        }
    }

    /**
     * Установка uid для хоста.
     *
     * @param hostInfo узел или extendedColumn, для которого
     *                 генерируется uid.
     */
    private void generateUid(HostInfo hostInfo) {
        if (hostInfo.getUid() == null) {
            hostInfo.setUid(UUID.randomUUID().toString());
        }
    }

    /**
     * Конвертация списка анкеров в json-строку.
     *
     * @return json представление анкеров с атрибутами в формате строки
     * @throws JsonProcessingException
     */
    public String anchorsAsJson() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(domain.getAnchor());
    }

    /**
     * Конвертация списка таев в json-строку.
     *
     * @return json представление таев в формате строки
     * @throws JsonProcessingException
     */
    public String tiesAsJson() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(domain.getTie());
    }

    /**
     * Конвертация списка кнотов в json-строку.
     *
     * @return json представление кнотов в формате строки
     * @throws JsonProcessingException
     */
    public String knotsAsJson() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(domain.getKnot());
    }

    /**
     * Конвертация списка tx-анкеров в json-строку.
     *
     * @return json представление tx-анкеров с атрибутами в формате строки
     * @throws JsonProcessingException
     */
    public String txAnchorsAsJson() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(domain.getTxAnchor());
    }

    /**
     * Конвертация списка cd-анкеров в json-строку.
     *
     * @return json представление cd-анкеров с атрибутами в формате строки
     * @throws JsonProcessingException
     */
    public String cdAnchorsAsJson() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(domain.getCdAnchor());
    }

    /**
     * Получение списка имён доменов в виде строки.
     *
     * @return имена доменов в виде строки
     * @throws JsonProcessingException
     */
    public String domainListAsJson() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(project.getDomain());
    }

    /**
     * Получение списка связей проекта.
     *
     * @return связи с проекта в формате строки
     * @throws JsonProcessingException
     */
    public String connexionsAsJson() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(project.getConnexions());
    }

    /**
     * Получение списка областей домена.
     *
     * @return связи с проекта в формате строки
     * @throws JsonProcessingException
     */
    public String areasAsJson() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(domain.getArea());
    }

    /**
     * Получение json представления насыщенных групп с насыщенными реквизитами.
     *
     * @return json-строка со списком групп.
     * @throws JsonProcessingException
     */
    public String groupsAsJson() throws JsonProcessingException {
        aggregateGroups();
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(actualGroups);
    }

    /**
     * Получение json представления реквизитов.
     *
     * @return json-строка со списком реквизитов.
     * @throws JsonProcessingException
     */
    public String propertiesAsJson() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(domain.getProperties());
    }

    /**
     * Получение списка баз данных модели деплоя.
     *
     * @return хранилища в формате json.
     * @throws JsonProcessingException
     */
    public String dbHostsAsJson() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(deploy.getDbHost());
    }

    /**
     * Получение списка хостов модели деплоя из файловых систем.
     *
     * @return хранилища в формате json.
     * @throws JsonProcessingException
     */
    public String fsHostsAsJson() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(deploy.getFsHost());
    }

    //  Группа методов для поиска узла (кроме атрибута)
    private Domain lookUpDomainByUid(String uid) {
        return project.getDomain().stream().filter(domain -> uid.equals(domain.getUid())).findAny().orElse(null);
    }

    private Anchor lookUpAnchorByUid(String uid) {
        return domain.getAnchor().stream().filter(anchor -> uid.equals(anchor.getUid())).findAny().orElse(null);
    }

    private CdAnchor lookUpCdAnchorByUid(String uid) {
        return domain.getCdAnchor().stream().filter(anchor -> uid.equals(anchor.getUid())).findAny().orElse(null);
    }

    private Tie lookUpTieByUid(String uid) {
        return domain.getTie().stream().filter(tie -> uid.equals(tie.getUid())).findAny().orElse(null);
    }

    private Knot lookUpKnotByUid(String uid) {
        return domain.getKnot().stream().filter(knot -> uid.equals(knot.getUid())).findAny().orElse(null);
    }

    private TxAnchor lookUpTxAnchorByUid(String uid) {
        return domain.getTxAnchor().stream().filter(txAnchor -> uid.equals(txAnchor.getUid())).findAny().orElse(null);
    }

    private Connexions lookUpConnexionByUid(String uid) {
        return project.getConnexions().stream().filter(connexion -> uid.equals(connexion.getUid())).findAny().orElse(null);
    }

    private DbHost lookUpDbHostByUid(String uid) {
        return deploy.getDbHost().stream().filter(dbHost -> uid.equals(dbHost.getUid())).findAny().orElse(null);
    }

    private FsHost lookUpFsHostByUid(String uid) {
        return deploy.getFsHost().stream().filter(fsHost -> uid.equals(fsHost.getUid())).findAny().orElse(null);
    }

    private Area lookUpAreaByUid(String uid) {
        return domain.getArea().stream().filter(area -> uid.equals(area.getUid())).findAny().orElse(null);
    }


    private Domain lookUpDomainByShortName(String shortName) {
        return project.getDomain().stream().filter(dom -> dom.getShortName().equals(shortName)).findAny().orElse(null);
    }

    // Группа методов для поиска индекса узла (кроме атрибута) в соответствующем списке.
    private Integer lookUpDomainIndexByUid(String uid) {
        return project.getDomain().indexOf(lookUpDomainByUid(uid));
    }

    private Integer lookUpAnchorIndexByUid(String uid) {
        return domain.getAnchor().indexOf(lookUpAnchorByUid(uid));
    }

    private Integer lookUpCdAnchorIndexByUid(String uid) {
        return domain.getCdAnchor().indexOf(lookUpCdAnchorByUid(uid));
    }

    private Integer lookUpTieIndexByUid(String uid) {
        return domain.getTie().indexOf(lookUpTieByUid(uid));
    }

    private Integer lookUpKnotIndexByUid(String uid) {
        return domain.getKnot().indexOf(lookUpKnotByUid(uid));
    }

    private Integer lookUpTxAnchorIndexByUid(String uid) {
        return domain.getTxAnchor().indexOf(lookUpTxAnchorByUid(uid));
    }

    private Integer lookUpConnexionIndexByUid(String uid) {
        return project.getConnexions().indexOf(lookUpConnexionByUid(uid));
    }

    private Integer lookUpDbHostIndexByUid(String uid) {
        return deploy.getDbHost().indexOf(lookUpDbHostByUid(uid));
    }

    private Integer lookUpFsHostIndexByUid(String uid) {
        return deploy.getFsHost().indexOf(lookUpFsHostByUid(uid));
    }


    private Integer lookUpAreaIndexByUid(String uid) {
        return domain.getArea().indexOf(lookUpAreaByUid(uid));
    }

    //  Группа методов для поиска кнота/анкора в соответствующем списке кнотов/анкеров.
    private Knot lookUpKnotByMnemonic(String mnemonic) {
        return domain.getKnot().stream().filter(knot -> mnemonic.equals(knot.getMnemonic())).findAny().orElse(null);
    }

    private Anchor lookUpAnchorByMnemonic(String mnemonic) {
        return domain.getAnchor().stream().filter(anchor -> mnemonic.equals(anchor.getMnemonic())).findAny().orElse(null);
    }

    private TxAnchor lookUpTxAnchorByMnemonic(String mnemonic) {
        return domain.getTxAnchor().stream().filter(anchor -> mnemonic.equals(anchor.getMnemonic())).findAny().orElse(null);
    }

    private CdAnchor lookUpCdAnchorByMnemonic(String mnemonic) {
        return domain.getCdAnchor().stream().filter(anchor -> mnemonic.equals(anchor.getMnemonic())).findAny().orElse(null);
    }

    //  Группа методов для поиска индекса узла анкора/кнота в соответствующем списке анкоров/кнотов.
    public Integer lookUpAnchorIndexByMnemonic(String mnemonic) {
        return domain.getAnchor().indexOf(lookUpAnchorByMnemonic(mnemonic));
    }

    public Integer lookUpKnotIndexByMnemonic(String mnemonic) {
        return domain.getKnot().indexOf(lookUpKnotByMnemonic(mnemonic));
    }

    public Integer lookUpTxAnchorIndexByMnemonic(String mnemonic) {
        return domain.getTxAnchor().indexOf(lookUpTxAnchorByMnemonic(mnemonic));
    }

    public Integer lookUpCdAnchorIndexByMnemonic(String mnemonic) {
        return domain.getCdAnchor().indexOf(lookUpCdAnchorByMnemonic(mnemonic));
    }

    //  Группа методов для поиска группы или реквизита по идентификатору.
    private Integer lookUpGroupIndexById(String id) {
        return domain.getGroup().indexOf(lookUpGroupById(id));
    }

    private Group lookUpGroupById(String id) {
        return domain.getGroup().stream().filter(group -> StringUtils.equals(id, group.getId())).findAny().orElse(null);
    }

    private Integer lookUpPropertyIndexById(String id) {
        if (domain.getProperties().isEmpty()) {
            return -1;
        }
        return domain.getProperties().get(0).getProperty().indexOf(lookUpPropertyById(id));
    }

    private Property lookUpPropertyById(String id) {
        if (domain.getProperties().isEmpty()) {
            return null;
        }
        return domain.getProperties().get(0).getProperty().stream().filter(property -> StringUtils.equals(id, property.getId())).findAny().orElse(null);
    }

    /**
     * Метод обновления и обогащения списка доменов в составе проекта.
     *
     * @param domainJson json-представление списка доменов в формате строки
     */
    public Map<String, String> updateDomain(String domainJson) {
        ObjectNode[] domainNodes = parseNodes(domainJson);
        Map<String, String> oldShortName = new HashMap<>();
        for (ObjectNode domainNode : domainNodes) {
            ObjectMapper mapper = new ObjectMapper();
            Domain dom = mapper.convertValue(domainNode, Domain.class);
            int index = lookUpDomainIndexByUid(domainNode.get("uid").textValue());
            Domain domToSet = lookUpDomainByUid(domainNode.get("uid").textValue());
            if (index == -1) {
                project.getDomain().add(dom);
            } else {
                oldShortName.put(project.getDomain().get(index).getShortName(), dom.getShortName());
                project.getConnexions().forEach(
                        connexions -> {
                            for (AnchorRole anchorRole : connexions.getAnchorRole()) {
                                if (StringUtils.equals(anchorRole.getDomain(), project.getDomain().get(index).getShortName())) {
                                    anchorRole.setDomain(dom.getShortName());
                                    break;
                                }
                            }
                        }
                );
                domToSet.setShortName(dom.getShortName());
                domToSet.setName(dom.getName());
                domToSet.setAuthor(dom.getAuthor());
                domToSet.setNote(dom.getNote());
                domToSet.setLayout(dom.getLayout());
                project.getDomain().set(index, domToSet);
            }
        }
        return oldShortName;
    }

    public void updateDomain(Domain dom) {
        int index = lookUpDomainIndexByUid(dom.getUid());
        if (index == -1) {
            project.getDomain().add(dom);
        } else {
            project.getDomain().set(index, dom);
        }
    }

    /**
     * Метод обновления и обогащения списка кнотов в составе домена.
     *
     * @param knotJson json-представление списка кнотов в формате строки
     */
    public void updateKnot(String knotJson) {
        ObjectNode[] knotNodes = parseNodes(knotJson);
        for (ObjectNode knotNode : knotNodes) {
            ObjectMapper mapper = new ObjectMapper();
            Knot knot = mapper.convertValue(knotNode, Knot.class);
            int index = lookUpKnotIndexByUid(knotNode.get("uid").textValue());
            if (index == -1) {
                domain.getKnot().add(knot);
            } else {
                domain.getKnot().set(index, knot);
            }
        }
    }

    /**
     * Метод обогащения списка кнотов в составе домена.
     *
     * @param knotJson json-представление списка новых кнотов в формате строки
     */
    public void addKnot(String knotJson) {
        ObjectNode[] knotNodes = parseNodes(knotJson);
        for (ObjectNode knotNode : knotNodes) {
            ObjectMapper mapper = new ObjectMapper();
            Knot knot = mapper.convertValue(knotNode, Knot.class);
            domain.getKnot().add(knot);
        }
    }

    /**
     * Метод для удаления кнота по uid.
     *
     * @param knotUid строка, состоящая из идентификаторов.
     */
    public void deleteKnot(String knotUid) {
        for (String uid : knotUid.split(",")) {
            domain.getKnot().remove(lookUpKnotByUid(uid));
        }
    }

    /**
     * Метод обновления и обогащения списка анкеров в составе домена.
     *
     * @param anchorJson json-представление списка анкеров в формате строки
     */
    public void updateAnchor(String anchorJson) {
        ObjectNode[] anchorNodes = parseNodes(anchorJson);
        for (ObjectNode anchorNode : anchorNodes) {
            ObjectMapper mapper = new ObjectMapper();
            Anchor anchor = mapper.convertValue(anchorNode, Anchor.class);
            int index = lookUpAnchorIndexByUid(anchorNode.get("uid").textValue());
            if (index == -1) {
                domain.getAnchor().add(anchor);
            } else {
                domain.getAnchor().set(index, anchor);
            }
        }
    }

    /**
     * Метод обогащения списка анкеров в составе домена.
     *
     * @param anchorJson json-представление списка новых анкеров в формате строки
     */
    public void addAnchor(String anchorJson) {
        ObjectNode[] anchorNodes = parseNodes(anchorJson);
        for (ObjectNode anchorNode : anchorNodes) {
            ObjectMapper mapper = new ObjectMapper();
            Anchor anchor = mapper.convertValue(anchorNode, Anchor.class);
            domain.getAnchor().add(anchor);
        }
    }

    /**
     * Метод для удаления анкера по uid.
     *
     * @param anchorUid строка, состоящая из идентификаторов.
     */
    public void deleteAnchor(String anchorUid) {
        for (String uid : anchorUid.split(",")) {
            domain.getAnchor().remove(lookUpAnchorByUid(uid));
        }
    }

    /**
     * Метод обновления и обогащения списка tx-анкеров.
     *
     * @param txAnchorJson строковое json-представление tx-анкеров.
     */
    public void updateTxAnchor(String txAnchorJson) {
        ObjectNode[] txAnchorNodes = parseNodes(txAnchorJson);
        for (ObjectNode txAnchorNode : txAnchorNodes) {
            ObjectMapper mapper = new ObjectMapper();
            TxAnchor txAnchor = mapper.convertValue(txAnchorNode, TxAnchor.class);
            int index = lookUpTxAnchorIndexByUid(txAnchorNode.get("uid").textValue());
            if (index == -1) {
                domain.getTxAnchor().add(txAnchor);
            } else {
                domain.getTxAnchor().set(index, txAnchor);
            }
        }
    }

    /**
     * Метод обогащения списка tx-анкеров.
     *
     * @param anchorJson строковое json-представление tx-анкеров.
     */
    public void addTxAnchor(String anchorJson) {
        ObjectNode[] txAnchorNodes = parseNodes(anchorJson);
        for (ObjectNode txAnchorNode : txAnchorNodes) {
            ObjectMapper mapper = new ObjectMapper();
            TxAnchor txAnchor = mapper.convertValue(txAnchorNode, TxAnchor.class);
            domain.getTxAnchor().add(txAnchor);
        }
    }

    /**
     * Метод удаления tx-анкера по uid.
     *
     * @param txAnchorUid строка, состоящая из идентификаторов.
     */
    public void deleteTxAnchor(String txAnchorUid) {
        for (String uid : txAnchorUid.split(",")) {
            domain.getTxAnchor().remove(lookUpTxAnchorByUid(uid));
        }
    }

    /**
     * Метод обновления и обогащения спсика таев.
     *
     * @param tieJson строкое json-представление списка таев.
     */
    public void updateTie(String tieJson) {
        ObjectNode[] tieNodes = parseNodes(tieJson);
        for (ObjectNode tieNode : tieNodes) {
            ObjectMapper mapper = new ObjectMapper();
            Tie tie = mapper.convertValue(tieNode, Tie.class);
            int index = lookUpTieIndexByUid(tieNode.get("uid").textValue());
            if (index == -1) {
                domain.getTie().add(tie);
            } else {
                domain.getTie().set(index, tie);
            }
        }
    }

    /**
     * Метод обогащения списка таев.
     *
     * @param anchorJson трокое json-представление списка таев.
     */
    public void addTie(String anchorJson) {
        ObjectNode[] tieNodes = parseNodes(anchorJson);
        for (ObjectNode tieNode : tieNodes) {
            ObjectMapper mapper = new ObjectMapper();
            Tie tie = mapper.convertValue(tieNode, Tie.class);
            domain.getTie().add(tie);
        }
    }

    /**
     * Метод для удаления таев по идентификаторам.
     *
     * @param tieUid строка, состоящая из идентификаторов.
     */
    public void deleteTie(String tieUid) {
        for (String uid : tieUid.split(",")) {
            domain.getTie().remove(lookUpTieByUid(uid));
        }
    }

    /**
     * Метод обновления и обогащения списка cd-анкеров.
     *
     * @param cdAnchorJson строковое json-представление cd-анкеров.
     */
    public void updateCdAnchor(String cdAnchorJson) {
        ObjectNode[] cdAnchorNodes = parseNodes(cdAnchorJson);
        for (ObjectNode cdAnchorNode : cdAnchorNodes) {
            ObjectMapper mapper = new ObjectMapper();
            CdAnchor cdAnchor = mapper.convertValue(cdAnchorNode, CdAnchor.class);
            int index = lookUpCdAnchorIndexByUid(cdAnchorNode.get("uid").textValue());
            if (index == -1) {
                domain.getCdAnchor().add(cdAnchor);
            } else {
                domain.getCdAnchor().set(index, cdAnchor);
            }
        }
    }

    /**
     * Метод обогащения списка cd-анкеров.
     *
     * @param anchorJson строковое json-представление cd-анкеров.
     */
    public void addCdAnchor(String anchorJson) {
        ObjectNode[] cdAnchorNodes = parseNodes(anchorJson);
        for (ObjectNode cdAnchorNode : cdAnchorNodes) {
            ObjectMapper mapper = new ObjectMapper();
            CdAnchor cdAnchor = mapper.convertValue(cdAnchorNode, CdAnchor.class);
            domain.getCdAnchor().add(cdAnchor);
        }
    }

    /**
     * Метод удаления cd-анкера по uid.
     *
     * @param cdAnchorUid строка, состоящая из идентификаторов.
     */
    public void deleteCdAnchor(String cdAnchorUid) {
        for (String uid : cdAnchorUid.split(",")) {
            domain.getCdAnchor().remove(lookUpCdAnchorByUid(uid));
        }
    }

    /**
     * Метод апдейта коннексионов.
     *
     * @param connexions строка, содержащая json-представления коннексионов.
     */
    public void updateConnexions(String connexions) {
        ObjectNode[] connexionsNodes = parseNodes(connexions);
        for (ObjectNode connexionNode : connexionsNodes) {
            ObjectMapper mapper = new ObjectMapper();
            Connexions connexion = mapper.convertValue(connexionNode, Connexions.class);
            int index = lookUpConnexionIndexByUid(connexionNode.get("uid").textValue());
            if (index == -1) {
                project.getConnexions().add(connexion);
            } else {
                project.getConnexions().set(index, connexion);
            }
        }
    }

    /**
     * Метод удаления коннексионов.
     *
     * @param connexionUid строка, содержащая идентификаторы коннексионов.
     */
    public void deleteConnexion(String connexionUid) {
        for (String uid : connexionUid.split(",")) {
            Connexions connexion = lookUpConnexionByUid(uid);
            if (connexion != null) {
                for (AnchorRole anchorRole : connexion.getAnchorRole()) {
                    if (anchorRole.isIdentifier()) {
                        deleteCdAnchorAndTieByAnchorRole(anchorRole);
                    }
                }
                project.getConnexions().remove(connexion);
            }
        }
    }

    /**
     * Метод обогащения списка доменов в составе проекта.
     *
     * @param domainJson json-представление списка новых доменов в формате строки
     */
    public void addDomain(String domainJson) {
        ObjectNode[] domainNodes = parseNodes(domainJson);
        for (ObjectNode domainNode : domainNodes) {
            ObjectMapper mapper = new ObjectMapper();
            Domain dom = mapper.convertValue(domainNode, Domain.class);
            project.getDomain().add(dom);
        }
    }

    /**
     * Метод удаления доменов и соответствующих коннексионов.
     *
     * @param shortNames строка кратких наименований доменов.
     */
    public void deleteDomain(String shortNames) {
        for (String shortName : shortNames.split(",")) {
            project.getConnexions().removeIf(connexions -> {
                for (int anchorRoleIndex = 0; anchorRoleIndex < connexions.getAnchorRole().size(); anchorRoleIndex++) {
                    AnchorRole anchorRole = connexions.anchorRole.get(anchorRoleIndex);
                    if (anchorRole.getDomain().equals(shortName)) {
                        if (anchorRole.isIdentifier()) {
                            deleteCdAnchorAndTieByAnchorRole(anchorRole);
                        } else {
                            deleteCdAnchorAndTieByAnchorRole(connexions.getAnchorRole().get(1 - anchorRoleIndex));
                        }
                        return true;
                    }
                }
                return false;
            });
            project.getDomain().remove(lookUpDomainByShortName(shortName));
        }
    }

    /**
     * Метод удаления доменов из схемы деплоя.
     *
     * @param shortNames строка кратких наименований доменов.
     */
    public void deleteDomainFromDeploy(String shortNames) {
        for (String shortName : shortNames.split(",")) {
            deploy.getDbHost().forEach(
                    dbHost -> {
                        dbHost.getDomain().removeIf(dom -> dom.getShortName().equals(shortName));
                    }
            );
            deploy.getFsHost().forEach(
                    fsHost -> {
                        fsHost.getDomain().removeIf(dom -> dom.getShortName().equals(shortName));
                    }
            );
        }
    }

    /**
     * Метод обогащения схемы деплоя элементами (атрибутами, анкерами, кнотами).
     *
     * @param deployItemJson json-представление списка новых элементов в формате строки.
     */
    public void addDeployItem(String deployItemJson) {
        ObjectNode[] deployItemsNodes = parseNodes(deployItemJson);
        ObjectMapper mapper = new ObjectMapper();
        Item[] items = mapper.convertValue(deployItemsNodes, Item[].class);
        for (Item item : items) {
            if (item.getDbType() != null) {
                for (DbHost dbHost : deploy.getDbHost()) {
                    if (StringUtils.equals(dbHost.getDbName(), item.getDbName())
                            && StringUtils.equals(dbHost.getHost(), item.getHost())
                            && StringUtils.equals(dbHost.getUserName(), item.getUserName())
                            && dbHost.getDbType() == item.getDbType()
                            && StringUtils.equals(dbHost.getPort(), item.getPort())) {
                        fillItems(item, dbHost.getDomain());
                    }
                }
            } else {
                for (FsHost fsHost : deploy.getFsHost()) {
                    if (StringUtils.equals(fsHost.getFolder(), item.getFolder())
                            && StringUtils.equals(fsHost.getHost(), item.getHost())
                            && StringUtils.equals(fsHost.getUserName(), item.getUserName())) {
                        fillItems(item, fsHost.getDomain());
                    }
                }
            }
        }
    }

    /**
     * Метод удаления элементов из схемы (атрибутов, анкеров, кнотов).
     *
     * @param deployItemJson json-представление списка элементов для удаления в формате строки.
     */
    public void deleteDeployItem(String deployItemJson) {
        ObjectNode[] deployItemsNodes = parseNodes(deployItemJson);
        ObjectMapper mapper = new ObjectMapper();
        Item[] items = mapper.convertValue(deployItemsNodes, Item[].class);
        for (Item item : items) {
            if (item.getDbType() != null) {
                for (DbHost dbHost : deploy.getDbHost()) {
                    if (StringUtils.equals(dbHost.getDbName(), item.getDbName())
                            && StringUtils.equals(dbHost.getHost(), item.getHost())
                            && StringUtils.equals(dbHost.getUserName(), item.getUserName())
                            && dbHost.getDbType() == item.getDbType()
                            && StringUtils.equals(dbHost.getPort(), item.getPort())) {
                        deleteItem(item, dbHost.getDomain());
                    }
                }
            } else {
                for (FsHost fsHost : deploy.getFsHost()) {
                    if (StringUtils.equals(fsHost.getFolder(), item.getFolder())
                            && StringUtils.equals(fsHost.getHost(), item.getHost())
                            && StringUtils.equals(fsHost.getUserName(), item.getUserName())) {
                        deleteItem(item, fsHost.getDomain());
                    }
                }
            }
        }
    }

    // Метод удаления анкера/атрибута/кнота из домена в модели деплоя.
    private void deleteItem(Item item, List<org.leandi.schema.deploy.Domain> domains) {
        org.leandi.schema.deploy.Domain dom = findDomain(domains, item.getShortName());
        if (dom != null) {
            dom.getItem().removeIf(depItem -> StringUtils.equals(depItem.getFqn(), item.getFqn()));
        }
        domains.removeIf(d -> d.getItem().isEmpty());
    }

    // Метод обогащения домена анкером/атрибутом/кнотом в модели деплоя.
    private void fillItems(Item item, List<org.leandi.schema.deploy.Domain> domains) {
        DeployItem deployItem = new DeployItem();
        deployItem.setFqn(item.getFqn());
        org.leandi.schema.deploy.Domain dom = findDomain(domains, item.getShortName());
        if (dom == null) {
            org.leandi.schema.deploy.Domain domainToAdd = new org.leandi.schema.deploy.Domain();
            domainToAdd.setShortName(item.getShortName());
            domainToAdd.getItem().add(deployItem);
            domains.add(domainToAdd);
        } else {
            dom.getItem().add(deployItem);
        }
    }

    public void renewDomains(Map<String, String> shortNames) {
        deploy.getDbHost().forEach(dbHost -> renewDomains(dbHost, shortNames));
        deploy.getFsHost().forEach(fsHost -> renewDomains(fsHost, shortNames));
    }

    private void renewDomains(HostInfo hostInfo, Map<String, String> shortNames) {
        for (Map.Entry<String, String> element : shortNames.entrySet()) {
            org.leandi.schema.deploy.Domain dom = findDomain(hostInfo.getDomain(), element.getKey());
            if (dom != null) {
                dom.setShortName(element.getValue());
            }
        }
    }

    // Метод поиска домена по сокращенному наименованию.
    private org.leandi.schema.deploy.Domain findDomain(List<org.leandi.schema.deploy.Domain> domains, String shortName) {
        for (org.leandi.schema.deploy.Domain dom : domains) {
            if (StringUtils.equals(dom.getShortName(), shortName)) {
                return dom;
            }
        }
        return null;
    }

    /**
     * Метод парсинга узлов из строкового представления списка,
     * хранящего json-ы узлов.
     *
     * @param nodesJson список узлов в формате json.
     * @return список узлов, к значениям которых можно обращаться по ключам.
     */
    private ObjectNode[] parseNodes(String nodesJson) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode[] nodes = null;
        try {
            nodes = mapper.readValue(nodesJson, ObjectNode[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось распарсить список узлов из строкового представления.");
        }
        return nodes;
    }

    /**
     * Метод удаления кросс-доменного анкера и связанного тая по anchorRole из коннексиона.
     *
     * @param anchorRole элемент коннексиона.
     */
    private void deleteCdAnchorAndTieByAnchorRole(AnchorRole anchorRole) {
        Domain dom = lookUpDomainByShortName(anchorRole.getDomain());
        if (dom != null) {
            dom.getCdAnchor().removeIf(cdAnchor -> cdAnchor.getMnemonic().equals(anchorRole.getType()));
            dom.getTie().removeIf(tie -> {
                for (AnchorRole tieAnchorRole : tie.getAnchorRole()) {
                    if (tieAnchorRole.getType().equals(anchorRole.getType())) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    /**
     * Метод обогащения списка fs-хостов.
     *
     * @param fsHostJson json-представление списка новых анкеров в формате строки
     */
    public void addFsHost(String fsHostJson) {
        ObjectNode[] fsNodes = parseNodes(fsHostJson);
        for (ObjectNode fsNode : fsNodes) {
            ObjectMapper mapper = new ObjectMapper();
            FsHost fsHost = mapper.convertValue(fsNode, FsHost.class);
            deploy.getFsHost().add(fsHost);
        }
    }

    /**
     * Метод обновления и обогащения списка fs-хостов.
     *
     * @param fsHostJson json-представление списка анкеров в формате строки
     */
    public void updateFsHost(String fsHostJson) {
        ObjectNode[] fsNodes = parseNodes(fsHostJson);
        for (ObjectNode fsNode : fsNodes) {
            ObjectMapper mapper = new ObjectMapper();
            FsHost fsHost = mapper.convertValue(fsNode, FsHost.class);
            int index = lookUpFsHostIndexByUid(fsNode.get("uid").textValue());
            if (index == -1) {
                deploy.getFsHost().add(fsHost);
            } else {
                deploy.getFsHost().set(index, fsHost);
            }
        }
    }

    /**
     * Метод удаления fs-хостов по uid.
     *
     * @param fsHostUid строка, состоящая из идентификаторов.
     */
    public void deleteFsHost(String fsHostUid) {
        for (String uid : fsHostUid.split(",")) {
            deploy.getFsHost().remove(lookUpFsHostByUid(uid));
        }
    }

    /**
     * Метод обогащения списка db-хостов.
     *
     * @param dbHostJson json-представление списка новых анкеров в формате строки
     */
    public void addDbHost(String dbHostJson) {
        ObjectNode[] dbNodes = parseNodes(dbHostJson);
        for (ObjectNode dbNode : dbNodes) {
            ObjectMapper mapper = new ObjectMapper();
            DbHost dbHost = mapper.convertValue(dbNode, DbHost.class);
            deploy.getDbHost().add(dbHost);
        }
    }

    /**
     * Метод обновления и обогащения списка db-хостов.
     *
     * @param dbHostJson json-представление списка анкеров в формате строки
     */
    public void updateDbHost(String dbHostJson) {
        ObjectNode[] dbNodes = parseNodes(dbHostJson);
        for (ObjectNode dbNode : dbNodes) {
            ObjectMapper mapper = new ObjectMapper();
            DbHost dbHost = mapper.convertValue(dbNode, DbHost.class);
            int index = lookUpDbHostIndexByUid(dbNode.get("uid").textValue());
            if (index == -1) {
                deploy.getDbHost().add(dbHost);
            } else {
                deploy.getDbHost().set(index, dbHost);
            }
        }
    }

    /**
     * Метод удаления db-хостов по uid.
     *
     * @param dbHostUid строка, состоящая из идентификаторов.
     */
    public void deleteDbHost(String dbHostUid) {
        for (String uid : dbHostUid.split(",")) {
            deploy.getDbHost().remove(lookUpDbHostByUid(uid));
        }
    }

    /**
     * Метод заполнения домена данными другого домена.
     *
     * @param domainData домен, из которого копируются данные.
     */
    public void fillDomainByAnotherDomain(SchemaUtils domainData) {
        Domain dom = domainData.getDomain();
        this.domain.getAnchor().addAll(dom.getAnchor());
        this.domain.getTie().addAll(dom.getTie());
        this.domain.getKnot().addAll(dom.getKnot());
        this.domain.getTxAnchor().addAll(dom.getTxAnchor());
        this.domain.getCdAnchor().addAll(dom.getCdAnchor());
        this.domain.getArea().addAll(dom.getArea());
        this.domain.getVerticalPropertiesGroup().addAll(dom.getVerticalPropertiesGroup());
        VerticalProperties verticalProperties = new VerticalProperties();
        if (dom.getVerticalProperties() != null) {
            verticalProperties.getVerticalProperty().addAll(dom.getVerticalProperties().getVerticalProperty());
        }
        this.domain.setVerticalProperties(verticalProperties);
    }

    /**
     * Метод для получения списка кратких наименований
     * доменов, содержащихся в хранилищах схемы деплоя.
     *
     * @return
     * @throws JsonProcessingException
     */
    public String getDomainsListFromDeploy() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Set<String> domainsList = new HashSet<>();
        deploy.getFsHost().forEach(fsHost -> fsHost.getDomain().forEach(dom -> domainsList.add(dom.getShortName())));
        deploy.getDbHost().forEach(dbHost -> dbHost.getDomain().forEach(dom -> domainsList.add(dom.getShortName())));
        return objectMapper.writeValueAsString(domainsList);
    }

    /**
     * Метод конвертации схемы домена в строковую XML-структуру.
     *
     * @return XML-структура в формате строки
     * @throws JAXBException
     */
    public String marshall() throws JAXBException {
        StringWriter stringWriter = new StringWriter();
        JAXBContext jaxbContext = JAXBContext.newInstance(Domain.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jaxbMarshaller.marshal(createDomainJaxbElement(), stringWriter);
        return stringWriter.toString();
    }

    /**
     * Метод конвертации схемы проекта в строковую XML-структуру.
     *
     * @return XML-структура в формате строки
     * @throws JAXBException
     */
    public String marshallProject() throws JAXBException {
        StringWriter stringWriter = new StringWriter();
        JAXBContext jaxbContext = JAXBContext.newInstance(Project.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jaxbMarshaller.marshal(createProjectJaxbElement(), stringWriter);
        return stringWriter.toString();
    }

    public String marshallDeployModel() throws JAXBException {
        StringWriter stringWriter = new StringWriter();
        JAXBContext jaxbContext = JAXBContext.newInstance(Deploy.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jaxbMarshaller.marshal(createDeployModelJaxbElement(), stringWriter);
        return stringWriter.toString();
    }

    /**
     * Метод получения списка анкеров в формате Mnemonic_Descriptor.
     *
     * @param domain домен, для которого
     * @return список полных наименований анкера.
     */
    public List<String> mnemonicPlusDescriptorList(Domain domain) {
        return domain.getAnchor().stream().map(
                anchor -> anchor.getMnemonic() + "_" + anchor.getDescriptor()
        ).collect(Collectors.toList());
    }

    // Вспомогательные методы createDomain..., createProject
    // были вынесены из marshall() и marshallProject() для удобства чтения.
    private JAXBElement<Project> createProjectJaxbElement() {
        try {
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTime(new Date());
            project.setDateTime(DatatypeFactory.newInstance().newXMLGregorianCalendar(cal));
        } catch (DatatypeConfigurationException e) {
            throw new IllegalArgumentException("Не удалось провести маршаллинг файла!");
        }
        return new JAXBElement<>(new QName("", "project"), Project.class, project);
    }

    private JAXBElement<Domain> createDomainJaxbElement() {
        try {
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTime(new Date());
            domain.setDateTime(DatatypeFactory.newInstance().newXMLGregorianCalendar(cal));
        } catch (DatatypeConfigurationException e) {
            throw new IllegalArgumentException("Не удалось провести маршаллинг файла!");
        }
        return new JAXBElement<>(new QName("", "domain"), Domain.class, domain);
    }

    private JAXBElement<Deploy> createDeployModelJaxbElement() {
        try {
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTime(new Date());
            deploy.setDateTime(DatatypeFactory.newInstance().newXMLGregorianCalendar(cal));
        } catch (DatatypeConfigurationException e) {
            throw new IllegalArgumentException("Не удалось провести маршаллинг файла!");
        }
        return new JAXBElement<>(new QName("", "deploy"), Deploy.class, deploy);
    }

    /**
     * Метод для добавления/изменения Area, перечисленных в строковом json.
     *
     * @param json массив объектов Area в формате строки.
     */
    public void updateArea(String json) {
        domain.getArea().clear();
        ObjectNode[] areaJsons = parseNodes(json);
        for (ObjectNode areaJson : areaJsons) {
            ObjectMapper mapper = new ObjectMapper();
            Area area = mapper.convertValue(areaJson, Area.class);
            domain.getArea().add(area);
        }
    }

    /**
     * Метод для удаления Area, про идентификатору.
     *
     * @param uid идентификатор Area.
     */
    public void deleteArea(String uid) {
        domain.getArea().removeIf(area -> StringUtils.equals(area.getUid(), uid));
    }

    // Вспомогательные классы для маппинга json-строк в java-объекты.
    @Getter
    public static class Item {
        private String shortName;
        private String host;
        private String dbName;
        private String userName;
        private String folder;
        private DbTypeType dbType;
        private String port;
        private String fqn;
    }

    @Getter
    private static class SerializeHelperClass {
        private String id;
        private List<String> elements;
    }

}