/*******************************************************************************
 * Copyright (c) 2011, 2017 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *  
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.service.datastore.internal;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.configuration.AbstractKapuaConfigurableService;
import org.eclipse.kapua.commons.util.ArgumentValidator;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.locator.KapuaProvider;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.account.AccountService;
import org.eclipse.kapua.service.authorization.AuthorizationService;
import org.eclipse.kapua.service.authorization.domain.Domain;
import org.eclipse.kapua.service.authorization.permission.Actions;
import org.eclipse.kapua.service.authorization.permission.Permission;
import org.eclipse.kapua.service.authorization.permission.PermissionFactory;
import org.eclipse.kapua.service.datastore.ChannelInfoRegistryService;
import org.eclipse.kapua.service.datastore.DatastoreDomain;
import org.eclipse.kapua.service.datastore.DatastoreObjectFactory;
import org.eclipse.kapua.service.datastore.MessageStoreService;
import org.eclipse.kapua.service.datastore.internal.elasticsearch.DatastoreMediator;
import org.eclipse.kapua.service.datastore.internal.elasticsearch.EsSchema;
import org.eclipse.kapua.service.datastore.internal.elasticsearch.MessageField;
import org.eclipse.kapua.service.datastore.internal.model.query.AndPredicateImpl;
import org.eclipse.kapua.service.datastore.internal.model.query.MessageQueryImpl;
import org.eclipse.kapua.service.datastore.internal.model.query.RangePredicateImpl;
import org.eclipse.kapua.service.datastore.internal.model.query.SortFieldImpl;
import org.eclipse.kapua.service.datastore.internal.model.query.StorableFieldImpl;
import org.eclipse.kapua.service.datastore.model.ChannelInfo;
import org.eclipse.kapua.service.datastore.model.ChannelInfoListResult;
import org.eclipse.kapua.service.datastore.model.MessageListResult;
import org.eclipse.kapua.service.datastore.model.StorableId;
import org.eclipse.kapua.service.datastore.model.query.AndPredicate;
import org.eclipse.kapua.service.datastore.model.query.ChannelInfoQuery;
import org.eclipse.kapua.service.datastore.model.query.MessageQuery;
import org.eclipse.kapua.service.datastore.model.query.RangePredicate;
import org.eclipse.kapua.service.datastore.model.query.SortDirection;
import org.eclipse.kapua.service.datastore.model.query.SortField;
import org.eclipse.kapua.service.datastore.model.query.StorableFetchStyle;
import org.eclipse.kapua.service.datastore.model.query.TermPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Channel info registry implementation
 * 
 * @since 1.0.0
 */
@KapuaProvider
public class ChannelInfoRegistryServiceImpl extends AbstractKapuaConfigurableService implements ChannelInfoRegistryService {

    private static final Domain datastoreDomain = new DatastoreDomain();

    private static final Logger logger = LoggerFactory.getLogger(ChannelInfoRegistryServiceImpl.class);

    private final AccountService accountService;
    private final AuthorizationService authorizationService;
    private final PermissionFactory permissionFactory;
    private final ChannelInfoRegistryFacade channelInfoStoreFacade;
    private final MessageStoreService messageStoreService;
    private final DatastoreObjectFactory datastoreObjectFactory;

    /**
     * Default constructor.
     * 
     * @since 1.0.0
     */
    public ChannelInfoRegistryServiceImpl() {
        super(ChannelInfoRegistryService.class.getName(), datastoreDomain, DatastoreEntityManagerFactory.getInstance());

        KapuaLocator locator = KapuaLocator.getInstance();
        accountService = locator.getService(AccountService.class);
        authorizationService = locator.getService(AuthorizationService.class);
        permissionFactory = locator.getFactory(PermissionFactory.class);
        messageStoreService = locator.getService(MessageStoreService.class);
        datastoreObjectFactory = KapuaLocator.getInstance().getFactory(DatastoreObjectFactory.class);

        MessageStoreService messageStoreService = KapuaLocator.getInstance().getService(MessageStoreService.class);
        ConfigurationProviderImpl configurationProvider = new ConfigurationProviderImpl(messageStoreService, accountService);
        this.channelInfoStoreFacade = new ChannelInfoRegistryFacade(configurationProvider, DatastoreMediator.getInstance());
        DatastoreMediator.getInstance().setChannelInfoStoreFacade(channelInfoStoreFacade);
    }

    @Override
    public void delete(KapuaId scopeId, StorableId id)
            throws KapuaException {
        ArgumentValidator.notNull(scopeId, "scopeId");
        ArgumentValidator.notNull(id, "id");

        checkDataAccess(scopeId, Actions.delete);

        try {
            channelInfoStoreFacade.delete(scopeId, id);
        } catch (Exception e) {
            throw KapuaException.internalError(e);
        }
    }

    @Override
    public ChannelInfo find(KapuaId scopeId, StorableId id)
            throws KapuaException {
        ArgumentValidator.notNull(scopeId, "scopeId");
        ArgumentValidator.notNull(id, "id");

        checkDataAccess(scopeId, Actions.read);

        try {
            ChannelInfo channelInfo = channelInfoStoreFacade.find(scopeId, id);

            // populate the lastMessageTimestamp
            updateLastPublishedFields(channelInfo);

            return channelInfo;
        } catch (Exception e) {
            throw KapuaException.internalError(e);
        }
    }

    @Override
    public ChannelInfoListResult query(ChannelInfoQuery query)
            throws KapuaException {
        ArgumentValidator.notNull(query, "query");
        ArgumentValidator.notNull(query.getScopeId(), "query.scopeId");

        checkDataAccess(query.getScopeId(), Actions.read);

        try {
            ChannelInfoListResult result = channelInfoStoreFacade.query(query);

            // populate the lastMessageTimestamp
            for (ChannelInfo channelInfo : result.getItems()) {
                updateLastPublishedFields(channelInfo);
            }

            return result;
        } catch (Exception e) {
            throw KapuaException.internalError(e);
        }
    }

    @Override
    public long count(ChannelInfoQuery query)
            throws KapuaException {
        ArgumentValidator.notNull(query, "query");
        ArgumentValidator.notNull(query.getScopeId(), "query.scopeId");

        checkDataAccess(query.getScopeId(), Actions.read);

        try {
            return channelInfoStoreFacade.count(query);
        } catch (Exception e) {
            throw KapuaException.internalError(e);
        }
    }

    @Override
    public void delete(ChannelInfoQuery query)
            throws KapuaException {
        ArgumentValidator.notNull(query, "query");
        ArgumentValidator.notNull(query.getScopeId(), "query.scopeId");

        checkDataAccess(query.getScopeId(), Actions.delete);

        try {
            channelInfoStoreFacade.delete(query);
        } catch (Exception e) {
            throw KapuaException.internalError(e);
        }
    }

    private void checkDataAccess(KapuaId scopeId, Actions action)
            throws KapuaException {
        //
        // Check Access
        Permission permission = permissionFactory.newPermission(datastoreDomain, action, scopeId);
        authorizationService.checkPermission(permission);
    }

    /**
     * Update the last published date and last published message identifier for the specified channel info, so it gets the timestamp and the message id of the last published message for the
     * account/clientId in the
     * channel info
     * 
     * @param scopeId
     * @param channelInfo
     * 
     * @throws KapuaException
     * 
     * @since 1.0.0
     */
    private void updateLastPublishedFields(ChannelInfo channelInfo) throws KapuaException {
        List<SortField> sort = new ArrayList<>();
        SortField sortTimestamp = new SortFieldImpl();
        sortTimestamp.setField(EsSchema.MESSAGE_TIMESTAMP);
        sortTimestamp.setSortDirection(SortDirection.DESC);
        sort.add(sortTimestamp);

        MessageQuery messageQuery = new MessageQueryImpl(channelInfo.getScopeId());
        messageQuery.setAskTotalCount(true);
        messageQuery.setFetchStyle(StorableFetchStyle.SOURCE_SELECT);
        messageQuery.setLimit(1);
        messageQuery.setOffset(0);
        messageQuery.setSortFields(sort);

        RangePredicate messageIdPredicate = new RangePredicateImpl(new StorableFieldImpl(EsSchema.CHANNEL_TIMESTAMP), channelInfo.getFirstMessageOn(), null);
        TermPredicate clientIdPredicate = datastoreObjectFactory.newTermPredicate(MessageField.CLIENT_ID, channelInfo.getClientId());
        TermPredicate channelPredicate = datastoreObjectFactory.newTermPredicate(MessageField.CHANNEL, channelInfo.getChannel());

        AndPredicate andPredicate = new AndPredicateImpl();
        andPredicate.getPredicates().add(messageIdPredicate);
        andPredicate.getPredicates().add(clientIdPredicate);
        andPredicate.getPredicates().add(channelPredicate);
        messageQuery.setPredicate(andPredicate);

        MessageListResult messageList = messageStoreService.query(messageQuery);

        StorableId lastPublishedMessageId = null;
        Date lastPublishedMessageTimestamp = null;
        if (messageList.getSize() == 1) {
            lastPublishedMessageId = messageList.getFirstItem().getDatastoreId();
            lastPublishedMessageTimestamp = messageList.getFirstItem().getTimestamp();
        } else if (messageList.isEmpty()) {
            // this condition could happens due to the ttl of the messages (so if it happens, it does not necessarily mean there has been an error!)
            logger.warn("Cannot find last timestamp for the specified client id '{}' - account '{}'", new Object[] { channelInfo.getScopeId(), channelInfo.getClientId() });
        } else {
            // this condition shouldn't never happens since the query has a limit 1
            // if happens it means than an elasticsearch internal error happens and/or our driver didn't set it correctly!
            logger.error("Cannot find last timestamp for the specified client id '{}' - account '{}'. More than one result returned by the query!",
                    new Object[] { channelInfo.getScopeId(), channelInfo.getClientId() });
        }

        channelInfo.setLastMessageId(lastPublishedMessageId);
        channelInfo.setLastMessageOn(lastPublishedMessageTimestamp);
    }
}
