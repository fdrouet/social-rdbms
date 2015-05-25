/*
 * Copyright (C) 2003-2015 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.social.addons.storage.dao.jpa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.TypedQuery;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.social.addons.storage.dao.ActivityDAO;
import org.exoplatform.social.addons.storage.dao.jpa.synchronization.SynchronizedGenericDAO;
import org.exoplatform.social.addons.storage.entity.Activity;
import org.exoplatform.social.addons.storage.entity.StreamItem;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.storage.ActivityStorageException;
import org.exoplatform.social.core.storage.api.RelationshipStorage;
import org.exoplatform.social.core.storage.api.SpaceStorage;

/**
 * Created by The eXo Platform SAS
 * Author : eXoPlatform
 *          exo@exoplatform.com
 * May 18, 2015  
 */
public class ActivityDAOImpl extends SynchronizedGenericDAO<Activity, Long> implements ActivityDAO {
  
  //Add customize methods here

  public List<Activity> getActivityByLikerId(String likerId) {
    TypedQuery<Activity> query = lifecycleLookup().getCurrentEntityManager().createNamedQuery("getActivitiesByLikerId", Activity.class);
    query.setParameter("likerId", likerId);
    return query.getResultList();
  }

  private List<Activity> getActivities(String strQuery, long offset, long limit) throws ActivityStorageException {
    return getActivities(strQuery, offset, limit, StreamItem.class);
  }

  private <E> List<Activity> getActivities(String strQuery, long offset, long limit, Class<E> clazz) throws ActivityStorageException {
    List<Activity> activities = new ArrayList<Activity>();
    if (strQuery == null || strQuery.isEmpty()) {
      return activities;
    }
    TypedQuery<E> typeQuery = lifecycleLookup().getCurrentEntityManager().createQuery(strQuery, clazz);
    if (limit > 0) {
      typeQuery.setFirstResult((int) offset);
      typeQuery.setMaxResults((int) limit);
    }
    List<E> ids = typeQuery.getResultList();
    for (E e : ids) {
      if (e instanceof Long) {
        activities.add(find((Long) e));
      } else if (e instanceof StreamItem) {
        activities.add(((StreamItem) e).getActivity());
      } else if (e instanceof Activity) {
        activities.add((Activity) e);
      }
    }
    return activities;
  }

  /**
   * Get activities of user that:
   *  + case 1: User is owner of activity or owner of activity-stream and not on space and not hidden.
   *  + case 2: User is owner of activity post on space and not hidden.
   * @param owner
   * @param offset
   * @param limit
   * @return
   * @throws ActivityStorageException
   */
  public List<Activity> getUserActivities(Identity owner, long time, boolean isNewer, long offset, long limit) throws ActivityStorageException {
    StringBuilder strQuery = new StringBuilder();//DISTINCT
    strQuery.append("select DISTINCT(a) from StreamItem s join s.activity a where ((a.ownerId ='")
            .append(owner.getId())
            .append("') or (s.ownerId = '")
            .append(owner.getId())
            .append("' and not s.streamType like '%SPACE%')) and (a.hidden = '0')")
            .append(buildSQLQueryByTime("a.lastUpdated", time, isNewer))
            .append(" order by a.lastUpdated desc");
    //
    return getActivities(strQuery.toString(), offset, limit, Activity.class);
  }
  
  @Override
  public int getNumberOfUserActivities(Identity ownerIdentity) {
    return getUserActivities(ownerIdentity, 0, false, 0, -1).size();
  }

  public List<Activity> getSpaceActivities(Identity owner, long time, boolean isNewer, long offset, long limit) throws ActivityStorageException {
    SpaceStorage spaceStorage = CommonsUtils.getService(SpaceStorage.class);
    Space space = spaceStorage.getSpaceByPrettyName(owner.getRemoteId());
    StringBuilder strQuery = new StringBuilder();//DISTINCT
    if (space != null) {
      strQuery.append("select s from StreamItem s join s.activity a where s.ownerId ='")
              .append(space.getId())
              .append("' and a.hidden = '0'")
              .append(buildSQLQueryByTime("a.lastUpdated", time, isNewer))
              .append(" order by a.lastUpdated desc");
    }
    //
    return getActivities(strQuery.toString(), offset, limit);
  }
  
  public List<Activity> getActivities(Identity owner, Identity viewer, long offset, long limit) throws ActivityStorageException {
    StringBuilder strQuery = new StringBuilder();//DISTINCT
    strQuery.append("select DISTINCT(a) from StreamItem s join s.activity a where s.ownerId = '")
            .append(owner.getId())
            .append("' and not s.streamType like '%SPACE%' and (a.ownerId ='")
            .append(owner.getId())
            .append("' or a.ownerId ='")
            .append(owner.getId())
            .append("') and (a.hidden = '0') order by a.lastUpdated desc");
    //
    return getActivities(strQuery.toString(), offset, limit, Activity.class);
  }

  public List<Activity> getActivityFeed(Identity ownerIdentity, int offset, int limit) {
    return getActivities(getFeedActivitySQLQuery(ownerIdentity, -1, false), offset, limit, Activity.class);
  }

  private String getFeedActivitySQLQuery(Identity ownerIdentity, long time, boolean isNewer) {
    RelationshipStorage relationshipStorage = CommonsUtils.getService(RelationshipStorage.class);
    SpaceStorage spaceStorage = CommonsUtils.getService(SpaceStorage.class);
    //
    List<Identity> relationships = relationshipStorage.getConnections(ownerIdentity);
    Set<String> relationshipIds = new HashSet<String>();
    for (Identity identity : relationships) {
      relationshipIds.add(identity.getId());
    }
    // get spaces where user is member
    List<Space> spaces = spaceStorage.getMemberSpaces(ownerIdentity.getRemoteId());
    String[] spaceIds = new String[0];
    for (Space space : spaces) {
      spaceIds = (String[]) ArrayUtils.add(spaceIds, space.getId());
    }
    StringBuilder sql = new StringBuilder();
    sql.append("select DISTINCT(a) from StreamItem s join s.activity a where ")
       .append(" ((s.ownerId='").append(ownerIdentity.getId()).append("'");
    
    if(CollectionUtils.isNotEmpty(spaces)){
      sql.append(" or s.ownerId in ('").append(StringUtils.join(spaceIds, "','")).append("') ");
    }
    if(CollectionUtils.isNotEmpty(relationships)){
      sql.append(" or (a.posterId in ('").append(StringUtils.join(relationshipIds, "','")).append("') ")
         .append("and s.streamType <> 0)");
    }
    sql.append(") and a.hidden='0'")
       .append(buildSQLQueryByTime("a.lastUpdated", time, isNewer))
       .append(")");
    sql.append(" order by a.lastUpdated desc");
    //
    return sql.toString();
  }

  public int getNumberOfActivitesOnActivityFeed(Identity ownerIdentity) {
    return getActivityFeed(ownerIdentity, 0, -1).size();
  }

  @Override
  public List<Activity> getUserSpacesActivities(Identity ownerIdentity, int offset, int limit) {
    return getActivities(getUserSpacesActivitySQLQuery(ownerIdentity, -1, false), offset, limit, Activity.class);
  }
  
  public int getNumberOfUserSpacesActivities(Identity ownerIdentity) {
    return getUserSpacesActivities(ownerIdentity, 0, -1).size();
  }
  
  private String getUserSpacesActivitySQLQuery(Identity ownerIdentity, long time, boolean isNewer) {
    SpaceStorage spaceStorage = CommonsUtils.getService(SpaceStorage.class);
    // get spaces where user is member
    List<Space> spaces = spaceStorage.getMemberSpaces(ownerIdentity.getRemoteId());
    if (spaces == null || spaces.size() == 0) {
      return StringUtils.EMPTY;
    }
    String[] spaceIds = new String[0];
    for (Space space : spaces) {
      spaceIds = (String[]) ArrayUtils.add(spaceIds, space.getId());
    }
    //
    StringBuilder sql = new StringBuilder();
    sql.append("select DISTINCT(a) from StreamItem s join s.activity a where ")
       .append("s.ownerId in ('").append(StringUtils.join(spaceIds, "','")).append("') ");
    
    sql.append(" and a.hidden='0'")
       .append(buildSQLQueryByTime("a.lastUpdated", time, isNewer));
    sql.append(" order by a.lastUpdated desc");
    //
    return sql.toString();
  }

  @Override
  public List<Activity> getActivitiesOfConnections(Identity ownerIdentity, int offset, int limit) {
    return getActivities(getConnectionsActivitySQLQuery(ownerIdentity, -1, false), offset, limit, Activity.class);
  }

  @Override
  public int getNumberOfActivitiesOfConnections(Identity ownerIdentity) {
    return getActivitiesOfConnections(ownerIdentity, 0, -1).size();
  }
  
  private String getConnectionsActivitySQLQuery(Identity ownerIdentity, long time, boolean isNewer) {
    RelationshipStorage relationshipStorage = CommonsUtils.getService(RelationshipStorage.class);
    //
    List<Identity> relationships = relationshipStorage.getConnections(ownerIdentity);
    if (relationships == null || relationships.size() == 0) {
      return StringUtils.EMPTY;
    }
    Set<String> relationshipIds = new HashSet<String>();
    for (Identity identity : relationships) {
      relationshipIds.add(identity.getId());
    }
    StringBuilder sql = new StringBuilder();
    sql.append("select DISTINCT(a) from StreamItem s join s.activity a where ")
       .append("a.posterId in ('").append(StringUtils.join(relationshipIds, "','")).append("') ")
       .append("and s.streamType <> 0");
    
    sql.append(" and a.hidden='0'")
       .append(buildSQLQueryByTime("a.lastUpdated", time, isNewer));
    sql.append(" order by a.lastUpdated desc");
    //
    return sql.toString();
  }

  @Override
  public int getNumberOfSpaceActivities(Identity spaceIdentity) {
    return getSpaceActivities(spaceIdentity, 0, false, 0, -1).size();
  }

  @Override
  public List<Activity> getNewerOnActivityFeed(Identity ownerIdentity, ExoSocialActivity baseActivity, int limit) {
    return getActivities(getFeedActivitySQLQuery(ownerIdentity, baseActivity.getUpdated().getTime(), true), 0, limit, Activity.class);
  }

  @Override
  public int getNumberOfNewerOnActivityFeed(Identity ownerIdentity, ExoSocialActivity baseActivity) {
    return getNewerOnActivityFeed(ownerIdentity, baseActivity, -1).size();
  }

  @Override
  public List<Activity> getOlderOnActivityFeed(Identity ownerIdentity, ExoSocialActivity baseActivity,int limit) {
    return getActivities(getFeedActivitySQLQuery(ownerIdentity, baseActivity.getUpdated().getTime(), false), 0, limit, Activity.class);
  }

  @Override
  public int getNumberOfOlderOnActivityFeed(Identity ownerIdentity, ExoSocialActivity baseActivity) {
    return getOlderOnActivityFeed(ownerIdentity, baseActivity, -1).size();
  }

  @Override
  public List<Activity> getNewerOnActivitiesOfConnections(Identity ownerIdentity, ExoSocialActivity baseActivity, long limit) {
    return getActivities(getConnectionsActivitySQLQuery(ownerIdentity, baseActivity.getUpdated().getTime(), true), 0, limit, Activity.class);
  }

  @Override
  public int getNumberOfNewerOnActivitiesOfConnections(Identity ownerIdentity, ExoSocialActivity baseActivity) {
    return getNewerOnActivitiesOfConnections(ownerIdentity, baseActivity, -1).size();
  }

  @Override
  public List<Activity> getOlderOnActivitiesOfConnections(Identity ownerIdentity, ExoSocialActivity baseActivity, int limit) {
    return getActivities(getConnectionsActivitySQLQuery(ownerIdentity, baseActivity.getUpdated().getTime(), false), 0, limit, Activity.class);
  }

  @Override
  public int getNumberOfOlderOnActivitiesOfConnections(Identity ownerIdentity, ExoSocialActivity baseActivity) {
    return getOlderOnActivitiesOfConnections(ownerIdentity, baseActivity, -1).size();
  }

  @Override
  public List<Activity> getNewerOnUserActivities(Identity ownerIdentity, ExoSocialActivity baseActivity, int limit) {
    return getUserActivities(ownerIdentity, baseActivity.getUpdated().getTime(), true, 0, limit);
  }

  @Override
  public int getNumberOfNewerOnUserActivities(Identity ownerIdentity, ExoSocialActivity baseActivity) {
    return getNewerOnUserActivities(ownerIdentity, baseActivity, -1).size();
  }

  @Override
  public List<Activity> getOlderOnUserActivities(Identity ownerIdentity, ExoSocialActivity baseActivity, int limit) {
    return getUserActivities(ownerIdentity, baseActivity.getUpdated().getTime(), false, 0, limit);
  }

  @Override
  public int getNumberOfOlderOnUserActivities(Identity ownerIdentity, ExoSocialActivity baseActivity) {
    return getOlderOnUserActivities(ownerIdentity, baseActivity, -1).size();
  }

  @Override
  public List<Activity> getNewerOnUserSpacesActivities(Identity ownerIdentity, ExoSocialActivity baseActivity, int limit) {
    return getActivities(getUserSpacesActivitySQLQuery(ownerIdentity, baseActivity.getUpdated().getTime(), true), 0, limit, Activity.class);
  }

  @Override
  public int getNumberOfNewerOnUserSpacesActivities(Identity ownerIdentity, ExoSocialActivity baseActivity) {
    return getNewerOnUserSpacesActivities(ownerIdentity, baseActivity, -1).size();
  }

  @Override
  public List<Activity> getOlderOnUserSpacesActivities(Identity ownerIdentity, ExoSocialActivity baseActivity, int limit) {
    return getActivities(getUserSpacesActivitySQLQuery(ownerIdentity, baseActivity.getUpdated().getTime(), false), 0, limit, Activity.class);
  }

  @Override
  public int getNumberOfOlderOnUserSpacesActivities(Identity ownerIdentity, ExoSocialActivity baseActivity) {
    return getOlderOnUserSpacesActivities(ownerIdentity, baseActivity, -1).size();
  }

  @Override
  public List<Activity> getNewerOnSpaceActivities(Identity spaceIdentity, ExoSocialActivity baseActivity, int limit) {
    return getSpaceActivities(spaceIdentity, baseActivity.getUpdated().getTime(), true, 0, limit);
  }

  @Override
  public int getNumberOfNewerOnSpaceActivities(Identity spaceIdentity, ExoSocialActivity baseActivity) {
    return getNewerOnSpaceActivities(spaceIdentity, baseActivity, -1).size();
  }

  @Override
  public List<Activity> getOlderOnSpaceActivities(Identity spaceIdentity, ExoSocialActivity baseActivity, int limit) {
    return getSpaceActivities(spaceIdentity, baseActivity.getUpdated().getTime(), false, 0, limit);
  }

  @Override
  public int getNumberOfOlderOnSpaceActivities(Identity spaceIdentity, ExoSocialActivity baseActivity) {
    return getOlderOnSpaceActivities(spaceIdentity, baseActivity, -1).size();
  }

}
