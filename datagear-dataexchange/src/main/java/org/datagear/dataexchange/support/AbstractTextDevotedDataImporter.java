/*
 * Copyright (c) 2018 datagear.org. All Rights Reserved.
 */

package org.datagear.dataexchange.support;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.datagear.dataexchange.DataImportReporter;
import org.datagear.dataexchange.DevotedDataImporter;
import org.datagear.dataexchange.support.DataFormat.BinaryFormat;
import org.datagear.dbinfo.ColumnInfo;
import org.datagear.dbinfo.DatabaseInfoResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 抽象文本{@linkplain DevotedDataImporter}。
 * 
 * @author datagear@163.com
 *
 * @param <T>
 */
public abstract class AbstractTextDevotedDataImporter<T extends AbstractTextDataImport>
		extends AbstractDevotedDataImporter<T>
{
	protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractTextDevotedDataImporter.class);

	public AbstractTextDevotedDataImporter()
	{
		super();
	}

	/**
	 * 构建{@linkplain InsertContext}。
	 * 
	 * @param impt
	 * @param table
	 * @return
	 */
	protected InsertContext buildInsertContext(AbstractTextDataImport impt, String table)
	{
		return new InsertContext(impt.getDataFormat(), table);
	}

	/**
	 * 执行插入{@linkplain PreparedStatement}。
	 * 
	 * @param impt
	 * @param st
	 * @param insertContext
	 * @throws InsertSqlException
	 */
	protected void executeInsertPreparedStatement(AbstractTextDataImport impt, PreparedStatement st,
			InsertContext insertContext) throws InsertSqlException
	{
		try
		{
			st.executeUpdate();
		}
		catch (SQLException e)
		{
			InsertSqlException e1 = new InsertSqlException(insertContext.getTable(), insertContext.getDataIndex(), e);

			if (impt.isAbortOnError())
				throw e1;
			else
			{
				if (impt.hasDataImportReporter())
					impt.getDataImportReporter().report(e1);
			}
		}
		finally
		{
			insertContext.incrementDataIndex();

			insertContext.clearCloseResources();
		}
	}

	/**
	 * 设置插入预编译SQL语句{@linkplain PreparedStatement}参数。
	 * <p>
	 * 如果{@linkplain AbstractTextDataImport#isAbortOnError()}为{@code false}，此方法将不会抛出{@linkplain SetInsertPreparedColumnValueException}。
	 * </p>
	 * 
	 * @param impt
	 * @param st
	 * @param columnInfos
	 * @param columnValues
	 * @param insertContext
	 * @throws SetInsertPreparedColumnValueException
	 */
	protected void setInsertPreparedColumnValues(AbstractTextDataImport impt, PreparedStatement st,
			ColumnInfo[] columnInfos, String[] columnValues, InsertContext insertContext)
			throws SetInsertPreparedColumnValueException
	{
		boolean abortOnError = impt.isAbortOnError();
		DataImportReporter dataImportReporter = (impt.hasDataImportReporter() ? impt.getDataImportReporter() : null);
		String table = insertContext.getTable();
		int dataIndex = insertContext.getDataIndex();

		for (int i = 0; i < columnInfos.length; i++)
		{
			ColumnInfo columnInfo = columnInfos[i];
			String columnName = columnInfo.getName();
			int sqlType = columnInfo.getType();
			int parameterIndex = i + 1;
			String rawValue = (columnValues == null || columnValues.length - 1 < i ? null : columnValues[i]);

			try
			{
				setPreparedStatementParameter(impt.getConnection(), st, parameterIndex, sqlType, rawValue,
						insertContext);
			}
			catch (SQLException e)
			{
				SetInsertPreparedColumnValueException e1 = new SetInsertPreparedColumnValueException(table, dataIndex,
						columnName, rawValue, e);

				if (abortOnError)
					throw e1;
				else
				{
					setParameterNull(st, parameterIndex, sqlType);

					if (dataImportReporter != null)
						dataImportReporter.report(e1);
				}
			}
			catch (ParseException e)
			{
				IllegalSourceValueException e1 = new IllegalSourceValueException(table, dataIndex, columnName, rawValue,
						e);

				if (abortOnError)
					throw e1;
				else
				{
					setParameterNull(st, parameterIndex, sqlType);

					if (dataImportReporter != null)
						dataImportReporter.report(e1);
				}
			}
			catch (DecoderException e)
			{
				IllegalSourceValueException e1 = new IllegalSourceValueException(table, dataIndex, columnName, rawValue,
						e);

				if (abortOnError)
					throw e1;
				else
				{
					setParameterNull(st, parameterIndex, sqlType);

					if (dataImportReporter != null)
						dataImportReporter.report(e1);
				}
			}
			catch (UnsupportedSqlTypeException e)
			{
				SetInsertPreparedColumnValueException e1 = new SetInsertPreparedColumnValueException(table, dataIndex,
						columnName, rawValue, e);

				if (abortOnError)
					throw e1;
				else
				{
					setParameterNull(st, parameterIndex, sqlType);

					if (dataImportReporter != null)
						dataImportReporter.report(e1);
				}
			}
			catch (Exception e)
			{
				SetInsertPreparedColumnValueException e1 = new SetInsertPreparedColumnValueException(table, dataIndex,
						columnName, rawValue, e);

				if (abortOnError)
					throw e1;
				else
				{
					setParameterNull(st, parameterIndex, sqlType);

					if (dataImportReporter != null)
						dataImportReporter.report(e1);
				}
			}
		}
	}

	/**
	 * 设置{@linkplain PreparedStatement}参数{@code null}。
	 * 
	 * @param st
	 * @param parameterIndex
	 * @param sqlType
	 */
	protected void setParameterNull(PreparedStatement st, int parameterIndex, int sqlType)
	{
		try
		{
			st.setNull(parameterIndex, sqlType);
		}
		catch (SQLException e)
		{
			LOGGER.error("set PreparedStatement parameter null for sql type [" + sqlType + "]", e);
		}
	}

	/**
	 * 设置{@linkplain PreparedStatement}参数，并进行必要的数据类型转换。
	 * <p>
	 * 此方法实现参考自JDBC4.0规范“Data Type Conversion Tables”章节中的“Java Types Mapper to
	 * JDBC Types”表。
	 * </p>
	 * 
	 * @param cn
	 * @param st
	 * @param parameterIndex
	 * @param sqlType
	 * @param parameterValue
	 * @param insertContext
	 * @throws SQLException
	 * @throws ParseException
	 * @throws DecoderException
	 * @throws UnsupportedSqlTypeException
	 */
	protected void setPreparedStatementParameter(Connection cn, PreparedStatement st, int parameterIndex, int sqlType,
			String parameterValue, InsertContext insertContext)
			throws SQLException, ParseException, DecoderException, UnsupportedSqlTypeException
	{
		if (parameterValue == null)
		{
			st.setNull(parameterIndex, sqlType);
			return;
		}

		DataFormat dataFormat = insertContext.getDataFormat();
		NumberFormat numberFormat = insertContext.getNumberFormatter();

		switch (sqlType)
		{
			case Types.CHAR:
			case Types.VARCHAR:
			case Types.LONGVARCHAR:

				st.setString(parameterIndex, parameterValue);
				break;

			case Types.NUMERIC:
			case Types.DECIMAL:

				BigDecimal bdv = new BigDecimal(parameterValue);
				st.setBigDecimal(parameterIndex, bdv);
				break;

			case Types.BIT:
			case Types.BOOLEAN:

				boolean bv = ("true".equalsIgnoreCase(parameterValue) || "1".equals(parameterValue)
						|| "on".equalsIgnoreCase(parameterValue));
				st.setBoolean(parameterIndex, bv);
				break;

			case Types.TINYINT:
			case Types.SMALLINT:
			case Types.INTEGER:

				numberFormat.setParseIntegerOnly(true);
				int iv = numberFormat.parse(parameterValue).intValue();
				st.setInt(parameterIndex, iv);
				break;

			case Types.BIGINT:

				numberFormat.setParseIntegerOnly(true);
				long lv = numberFormat.parse(parameterValue).longValue();
				st.setLong(parameterIndex, lv);
				break;

			case Types.REAL:

				numberFormat.setParseIntegerOnly(false);
				float fv = numberFormat.parse(parameterValue).floatValue();
				st.setFloat(parameterIndex, fv);
				break;

			case Types.FLOAT:
			case Types.DOUBLE:

				numberFormat.setParseIntegerOnly(false);
				double dv = numberFormat.parse(parameterValue).doubleValue();
				st.setDouble(parameterIndex, dv);
				break;

			case Types.BINARY:
			case Types.VARBINARY:
			case Types.LONGVARBINARY:

				if (BinaryFormat.HEX.equals(dataFormat.getBinaryFormat()))
				{
					byte[] btv = convertToBytesForHex(parameterValue);
					st.setBytes(parameterIndex, btv);
				}
				else if (BinaryFormat.BASE64.equals(dataFormat.getBinaryFormat()))
				{
					byte[] btv = convertToBytesForBase64(parameterValue);
					st.setBytes(parameterIndex, btv);
				}
				else
					throw new UnsupportedOperationException(
							"Binary type [" + dataFormat.getBinaryFormat() + "] is not supported");

				break;

			case Types.DATE:

				java.util.Date dtv = insertContext.getDateFormatter().parse(parameterValue);
				java.sql.Date sdtv = new java.sql.Date(dtv.getTime());
				st.setDate(parameterIndex, sdtv);
				break;

			case Types.TIME:

				java.util.Date tdv = insertContext.getTimeFormatter().parse(parameterValue);
				java.sql.Time tv = new java.sql.Time(tdv.getTime());
				st.setTime(parameterIndex, tv);
				break;

			case Types.TIMESTAMP:

				// 如果是默认格式，则直接使用Timestamp.valueOf，这样可以避免丢失纳秒精度
				if (DataFormat.DEFAULT_TIMESTAMP_FORMAT.equals(dataFormat.getTimestampFormat()))
				{
					java.sql.Timestamp tsv = Timestamp.valueOf(parameterValue);
					st.setTimestamp(parameterIndex, tsv);
				}
				else
				{
					// XXX 这种处理方式会丢失纳秒数据，待以后版本升级至jdk1.8库时采用java.time可解决
					java.util.Date tsdv = insertContext.getTimestampFormatter().parse(parameterValue);
					java.sql.Timestamp tsv = new Timestamp(tsdv.getTime());
					st.setTimestamp(parameterIndex, tsv);
				}
				break;

			case Types.CLOB:

				Clob clob = cn.createClob();
				clob.setString(1, parameterValue);
				st.setClob(parameterIndex, clob);
				break;

			case Types.BLOB:

				if (BinaryFormat.HEX.equals(dataFormat.getBinaryFormat()))
				{
					Blob blob = cn.createBlob();
					byte[] btv = convertToBytesForHex(parameterValue);
					blob.setBytes(1, btv);
					st.setBlob(parameterIndex, blob);
				}
				else if (BinaryFormat.BASE64.equals(dataFormat.getBinaryFormat()))
				{
					Blob blob = cn.createBlob();
					byte[] btv = convertToBytesForBase64(parameterValue);
					blob.setBytes(1, btv);
					st.setBlob(parameterIndex, blob);
				}
				else
					throw new UnsupportedOperationException();

				break;

			case Types.NCHAR:
			case Types.NVARCHAR:
			case Types.LONGNVARCHAR:

				st.setNString(parameterIndex, parameterValue);
				break;

			case Types.NCLOB:

				NClob nclob = cn.createNClob();
				nclob.setString(1, parameterValue);
				st.setNClob(parameterIndex, nclob);
				break;

			case Types.SQLXML:

				SQLXML sqlxml = cn.createSQLXML();
				sqlxml.setString(parameterValue);
				st.setSQLXML(parameterIndex, sqlxml);
				break;

			default:

				throw new UnsupportedSqlTypeException(sqlType);
		}
	}

	/**
	 * 构建插入预编译SQL语句。
	 * 
	 * @param cn
	 * @param table
	 * @param columnInfos
	 * @return
	 * @throws SQLException
	 */
	protected String buildInsertPreparedSql(Connection cn, String table, ColumnInfo[] columnInfos) throws SQLException
	{
		String quote = cn.getMetaData().getIdentifierQuoteString();

		StringBuilder sql = new StringBuilder("INSERT INTO ");
		sql.append(quote).append(table).append(quote);
		sql.append(" (");

		for (int i = 0; i < columnInfos.length; i++)
		{
			if (i != 0)
				sql.append(',');

			sql.append(quote).append(columnInfos[i].getName()).append(quote);
		}

		sql.append(") VALUES (");

		for (int i = 0; i < columnInfos.length; i++)
		{
			if (i != 0)
				sql.append(',');

			sql.append('?');
		}

		sql.append(")");

		return sql.toString();
	}

	/**
	 * 将HEX编码的字符串转换为字节数组。
	 * 
	 * @param value
	 * @return
	 * @throws DecoderException
	 */
	protected byte[] convertToBytesForHex(String value) throws DecoderException
	{
		if (value == null || value.isEmpty())
			return null;

		return Hex.decodeHex(value);
	}

	/**
	 * 将Base64编码的字符串转换为字节数组。
	 * 
	 * @param value
	 * @return
	 */
	protected byte[] convertToBytesForBase64(String value)
	{
		if (value == null || value.isEmpty())
			return null;

		return Base64.decodeBase64(value);
	}

	/**
	 * 移除{@code null}列信息位置对应的列值。
	 * 
	 * @param rawColumnInfos
	 * @param noNullColumnInfos
	 * @param rawColumnValues
	 * @return
	 */
	protected String[] removeNullColumnInfoValues(ColumnInfo[] rawColumnInfos, ColumnInfo[] noNullColumnInfos,
			String[] rawColumnValues)
	{
		if (noNullColumnInfos == rawColumnInfos || noNullColumnInfos.length == rawColumnInfos.length)
			return rawColumnValues;

		String[] newColumnValues = new String[noNullColumnInfos.length];

		int index = 0;

		for (int i = 0; i < rawColumnInfos.length; i++)
		{
			if (rawColumnInfos[i] == null)
				continue;

			newColumnValues[index++] = rawColumnValues[i];
		}

		return newColumnValues;
	}

	/**
	 * 移除{@linkplain ColumnInfo}数组中的{@code null}元素。
	 * <p>
	 * 如果没有{@code null}元素，将返回原数组。
	 * </p>
	 * 
	 * @param columnInfos
	 * @return
	 */
	protected ColumnInfo[] removeNulls(ColumnInfo[] columnInfos)
	{
		boolean noNull = true;

		for (ColumnInfo columnInfo : columnInfos)
		{
			if (columnInfo == null)
			{
				noNull = false;
				break;
			}
		}

		if (noNull)
			return columnInfos;

		List<ColumnInfo> list = new ArrayList<ColumnInfo>(columnInfos.length);

		for (ColumnInfo columnInfo : columnInfos)
		{
			if (columnInfo != null)
				list.add(columnInfo);
		}

		return list.toArray(new ColumnInfo[list.size()]);
	}

	/**
	 * 获取表指定列信息数组。
	 * <p>
	 * 当指定位置的列不存在时，如果{@code nullIfInexistentColumn}为{@code true}，返回数组对应位置将为{@code null}，
	 * 否则，将立刻抛出{@linkplain ColumnNotFoundException}。
	 * </p>
	 * 
	 * @param cn
	 * @param table
	 * @param columnNames
	 * @param nullIfInexistentColumn
	 * @param databaseInfoResolver
	 * @return
	 * @throws ColumnNotFoundException
	 */
	protected ColumnInfo[] getColumnInfos(Connection cn, String table, String[] columnNames,
			boolean nullIfInexistentColumn, DatabaseInfoResolver databaseInfoResolver) throws ColumnNotFoundException
	{
		ColumnInfo[] columnInfos = new ColumnInfo[columnNames.length];

		ColumnInfo[] allColumnInfos = databaseInfoResolver.getColumnInfos(cn, table);

		for (int i = 0; i < columnNames.length; i++)
		{
			ColumnInfo columnInfo = null;

			for (int j = 0; j < allColumnInfos.length; j++)
			{
				if (allColumnInfos[j].getName().equals(columnNames[i]))
				{
					columnInfo = allColumnInfos[j];
					break;
				}
			}

			if (!nullIfInexistentColumn && columnInfo == null)
				throw new ColumnNotFoundException(table, columnNames[i]);

			columnInfos[i] = columnInfo;
		}

		return columnInfos;
	}

	/**
	 * 插入SQL语句{@linkplain PreparedStatement}参数支持上下文。
	 * 
	 * @author datagear@163.com
	 *
	 */
	protected static class InsertContext extends DataFormatContext
	{
		private List<Closeable> closeResources = new LinkedList<Closeable>();

		private String table;

		private int dataIndex = 0;

		public InsertContext()
		{
			super();
		}

		public InsertContext(DataFormat dataFormat, String table)
		{
			super(dataFormat);
			this.table = table;
		}

		public String getTable()
		{
			return table;
		}

		public void setTable(String table)
		{
			this.table = table;
		}

		public int getDataIndex()
		{
			return dataIndex;
		}

		public void setDataIndex(int dataIndex)
		{
			this.dataIndex = dataIndex;
		}

		/**
		 * 数据索引加{@code 1}。
		 */
		public void incrementDataIndex()
		{
			this.dataIndex += 1;
		}

		/**
		 * 添加一个待关闭的{@linkplain Closeable}。
		 * 
		 * @param closeable
		 */
		public void addCloseResource(Closeable closeable)
		{
			this.closeResources.add(closeable);
		}

		/**
		 * 清除并关闭所有{@linkplain Closeable}。
		 * 
		 * @return
		 */
		public int clearCloseResources()
		{
			int size = closeResources.size();

			for (int i = 0; i < size; i++)
			{
				Closeable closeable = this.closeResources.get(i);

				try
				{
					closeable.close();
				}
				catch (IOException e)
				{
				}
			}

			return size;
		}
	}
}