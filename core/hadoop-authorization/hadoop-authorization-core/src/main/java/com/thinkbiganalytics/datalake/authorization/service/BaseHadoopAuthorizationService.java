package com.thinkbiganalytics.datalake.authorization.service;

import com.thinkbiganalytics.metadata.api.event.MetadataEventListener;
import com.thinkbiganalytics.metadata.api.event.MetadataEventService;
import com.thinkbiganalytics.metadata.api.event.feed.FeedPropertyChangeEvent;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

/**
 * Created by Jeremy Merrifield on 10/14/16.
 */
public abstract class BaseHadoopAuthorizationService implements HadoopAuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(BaseHadoopAuthorizationService.class);

    public static final String REGISTRATION_HDFS_FOLDERS = "nifi:registration:hdfsFolders";
    public static final String REGISTRATION_HIVE_SCHEMA = "nifi:registration:hiveSchema";
    public static final String REGISTRATION_HIVE_TABLES = "nifi:registration:hiveTableNames";

    /**
     * Event listener for precondition events
     */
    private final MetadataEventListener<FeedPropertyChangeEvent> feedPropertyChangeListener = new FeedPropertyChangeDispatcher();

    @Inject
    private MetadataEventService metadataEventService;

    /**
     * Adds listeners for transferring events.
     */
    @PostConstruct
    public void addEventListener() {
        metadataEventService.addListener(feedPropertyChangeListener);
    }

    /**
     * Removes listeners and stops transferring events.
     */
    @PreDestroy
    public void removeEventListener() {
        metadataEventService.removeListener(feedPropertyChangeListener);
    }

    private class FeedPropertyChangeDispatcher implements MetadataEventListener<FeedPropertyChangeEvent> {

        @Override
        public void notify(final FeedPropertyChangeEvent metadataEvent) {
            if (metadataEvent.getHadoopSecurityGroupNames() != null && hadoopAuthorizationChangesRequired(metadataEvent)) {
                try {
                    validateFieldsNotNull(metadataEvent.getNewProperties().getProperty(REGISTRATION_HIVE_TABLES), metadataEvent.getNewProperties().getProperty(REGISTRATION_HIVE_SCHEMA),
                                          metadataEvent.getNewProperties().getProperty(REGISTRATION_HDFS_FOLDERS));

                    String hdfsFoldersWithCommas = metadataEvent.getNewProperties().getProperty(REGISTRATION_HDFS_FOLDERS).replace("\n", ",");
                    List<String> hdfsFolders = Stream.of(hdfsFoldersWithCommas).collect(Collectors.toList());

                    String hiveTablesWithCommas = metadataEvent.getNewProperties().getProperty(REGISTRATION_HIVE_TABLES).replace("\n", ",");
                    List<String> hiveTables = Stream.of(hiveTablesWithCommas).collect(Collectors.toList());
                    String hiveSchema = metadataEvent.getNewProperties().getProperty(REGISTRATION_HIVE_SCHEMA);

                    createOrUpdateReadOnlyHivePolicy(metadataEvent.getFeedCategory(), metadataEvent.getFeedName()
                        , metadataEvent.getHadoopSecurityGroupNames()
                        , hiveSchema
                        , hiveTables);
                    createOrUpdateReadOnlyHdfsPolicy(metadataEvent.getFeedCategory(), metadataEvent.getFeedName()
                        , metadataEvent.getHadoopSecurityGroupNames()
                        , hdfsFolders);
                } catch (Exception e) {
                    log.error("Error creating Ranger policy after metadata property change event", e);
                    throw new RuntimeException("Error creating Ranger policy after metadata property change event");
                }
            }
        }

        private void validateFieldsNotNull(String hiveTables, String hiveSchema, String hdfsFolders) {
            if (StringUtils.isEmpty(hiveTables) || StringUtils.isEmpty(hiveSchema) || StringUtils.isEmpty(hdfsFolders)) {
                throw new IllegalArgumentException("Three properties are required in the metadata to create Ranger policies. " + REGISTRATION_HDFS_FOLDERS + ", " + REGISTRATION_HIVE_SCHEMA +
                                                   ", and" + REGISTRATION_HIVE_TABLES);
            }
        }

        private boolean hadoopAuthorizationChangesRequired(final FeedPropertyChangeEvent metadataEvent) {
            String hdfsFoldersWithCommasNew = metadataEvent.getNewProperties().getProperty(REGISTRATION_HDFS_FOLDERS);
            String hiveTablesWithCommasNew = metadataEvent.getNewProperties().getProperty(REGISTRATION_HIVE_TABLES);
            String hiveSchemaNew = metadataEvent.getNewProperties().getProperty(REGISTRATION_HIVE_SCHEMA);

            if (hdfsFoldersWithCommasNew != null && hiveSchemaNew != null && hiveTablesWithCommasNew != null) {
                return true;
            }
            return false;
        }
    }
}