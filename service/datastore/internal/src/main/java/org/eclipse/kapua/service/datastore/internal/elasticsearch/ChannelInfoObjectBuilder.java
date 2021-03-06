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
package org.eclipse.kapua.service.datastore.internal.elasticsearch;

import java.util.Date;
import java.util.Map;

import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.datastore.internal.model.ChannelInfoImpl;
import org.eclipse.kapua.service.datastore.internal.model.StorableIdImpl;
import org.eclipse.kapua.service.datastore.model.ChannelInfo;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;

/**
 * Channel information object builder.<br>
 * This object converts the schema coming from an Elasticsearch search hit to a Kapua channel information object (unmarshal).
 *
 * @since 1.0.0
 */
public class ChannelInfoObjectBuilder {

    private ChannelInfoImpl channelInfo;

    /**
     * Build a {@link ChannelInfoObjectBuilder} from the Elasticsearch search hit
     *
     * @param searchHit
     * @return
     * @throws EsObjectBuilderException
     * @since 1.0.0
     */
    public ChannelInfoObjectBuilder build(SearchHit searchHit)
            throws EsObjectBuilderException {
        String id = searchHit.getId();

        Map<String, SearchHitField> fields = searchHit.getFields();
        String channel = fields.get(EsSchema.CHANNEL_NAME).getValue();
        String lastMsgId = fields.get(EsSchema.CHANNEL_MESSAGE_ID).getValue();
        String lastMsgTimestampStr = fields.get(EsSchema.CHANNEL_TIMESTAMP).getValue();
        String clientId = fields.get(EsSchema.CHANNEL_CLIENT_ID).getValue();
        KapuaId scopeId = KapuaEid.parseCompactId(fields.get(EsSchema.CHANNEL_SCOPE_ID).getValue());

        channelInfo = new ChannelInfoImpl(scopeId, new StorableIdImpl(id));
        channelInfo.setClientId(clientId);
        channelInfo.setChannel(channel);
        channelInfo.setFirstMessageId(new StorableIdImpl(lastMsgId));

        Date timestamp = (Date) EsUtils.convertToKapuaObject("date", lastMsgTimestampStr);
        channelInfo.setFirstMessageOn(timestamp);

        return this;
    }

    /**
     * Get the built Kapua channel information object
     *
     * @return
     * @since 1.0.0
     */
    public ChannelInfo getChannelInfo() {
        return this.channelInfo;
    }
}
