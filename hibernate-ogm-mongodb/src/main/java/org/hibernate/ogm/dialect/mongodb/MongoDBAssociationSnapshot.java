/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.hibernate.ogm.dialect.mongodb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.ogm.datastore.spi.AssociationSnapshot;
import org.hibernate.ogm.datastore.spi.Tuple;
import org.hibernate.ogm.grid.AssociationKey;
import org.hibernate.ogm.grid.RowKey;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

import static org.hibernate.ogm.dialect.mongodb.MongoHelpers.getAssociationFieldOrNull;
import static org.hibernate.ogm.dialect.mongodb.MongoHelpers.isEmbedded;

/**
 * @author Alan Fitton <alan at eth0.org.uk>
 * @author Emmanuel Bernard <emmanuel@hibernate.org>
 */
public class MongoDBAssociationSnapshot implements AssociationSnapshot {

	private final Map<RowKey, DBObject> map;
	private final DBObject assoc;
	private AssociationKey associationKey;

	/**
	 * @param assoc DBObject containing the association information
	 * @param key
	 */
	public MongoDBAssociationSnapshot(DBObject document, AssociationKey key) {
		this.assoc = document;
		this.map = new LinkedHashMap<RowKey, DBObject>();
		this.associationKey = key;

		for ( DBObject row : getRows() ) {
			Collection<String> columnNames;
			DBObject mongodbColumnData;
			if ( isEmbedded( key ) ) {
				//in the embedded case, we read RowKey metadata from AssociationKey and data from the tuple
				columnNames = Arrays.asList( key.getRowKeyColumnNames() );
				mongodbColumnData = row;
			}
			else {
				//we read RowKey metadata and data from the tuple
				mongodbColumnData = (DBObject)row.get( MongoDBDialect.COLUMNS_FIELDNAME );
				columnNames = mongodbColumnData.keySet();
			}

			List<Object> columnValues = new ArrayList<Object>();
			for ( String columnKey : columnNames ) {
				boolean getFromMongoData = true;
				int length = key.getColumnNames().length;
				for ( int index = 0 ; index < length; index++ ) {
					String assocColumn = key.getColumnNames()[index];
					if ( assocColumn.equals( columnKey ) ) {
						columnValues.add( associationKey.getColumnValues()[index] );
						getFromMongoData = false;
						break;
					}
				}
				if ( getFromMongoData == true ) {
					columnValues.add( mongodbColumnData.get( columnKey ) );
				}
			}
			RowKey rowKey = new RowKey(
					key.getTable(),
					columnNames.toArray( new String[columnNames.size()] ),
					columnValues.toArray() );

			this.map.put( rowKey, row );
		}
	}

	@Override
	public Tuple get(RowKey column) {
		DBObject row = this.map.get( column );
		if ( ! isEmbedded( associationKey ) ) {
			row = (DBObject) row.get( MongoDBDialect.TUPLE_FIELDNAME );
		}
		return row == null ? null : new Tuple( new MongoDBTupleSnapshot( row, column ) );
	}

	//not for embedded
	public DBObject getQueryObject() {
		DBObject query = new BasicDBObject();
		query.put( MongoDBDialect.ID_FIELDNAME, assoc.get( MongoDBDialect.ID_FIELDNAME ) );
		return query;
	}

	@Override
	public boolean containsKey(RowKey column) {
		return map.containsKey( column );
	}

	@Override
	public int size() {
		return map.size();
	}

	@SuppressWarnings("unchecked")
	private Collection<DBObject> getRows() {
		if ( isEmbedded( associationKey ) ) {
			return getAssociationFieldOrNull( associationKey, assoc );
		}
		else {
			return (Collection<DBObject>) assoc.get( MongoDBDialect.ROWS_FIELDNAME );
		}
	}

	public DBObject getRowKeyDBObject(RowKey rowKey) {
		return map.get( rowKey );
	}

	@Override
	public Set<RowKey> getRowKeys() {
		return map.keySet();
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append( "MongoDBAssociationSnapshot(" );
		sb.append( map.size() );
		sb.append( " RowKey entries)." );
		return sb.toString();
	}
}