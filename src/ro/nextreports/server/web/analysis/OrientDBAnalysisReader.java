package ro.nextreports.server.web.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import ro.nextreports.server.domain.Analysis;
import ro.nextreports.server.domain.ReportResultEvent;
import ro.nextreports.server.service.AnalysisService;
import ro.nextreports.server.service.ReportService;
import ro.nextreports.server.service.StorageService;
import ro.nextreports.server.util.AnalysisUtil;
import ro.nextreports.server.web.analysis.util.AnalysisException;
import ro.nextreports.server.web.analysis.util.DatabaseUtil;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

public class OrientDBAnalysisReader implements AnalysisReader {

	private StorageService storageService;
	private AnalysisService analysisService;
	private ReportService reportService;

	private ODatabaseDocumentTx db;

	private int rowCount = -1;	

	private static final Logger LOG = LoggerFactory.getLogger(OrientDBAnalysisReader.class);	
	
	public OrientDBAnalysisReader() {
	}

	@Override
	public List<String> getHeader(Analysis analysis) {
		if (analysis == null) {
			return new ArrayList<String>();
		}				
		List<String> columnNames = analysis.getColumns();
		if ((columnNames == null) || columnNames.isEmpty()) {			
			long start = System.currentTimeMillis();
			initConnection();
			if (db == null) {
				return new ArrayList<String>();
			}
			System.out.println("---------- getHeader");
			Columns columns = getColumns(analysis);
			columnNames = columns.getColumnNames();
			analysis.setColumns(columnNames);
			analysis.setColumnTypes(columns.getColumnTypes());
			// select all by default
			List<Boolean> selected = new ArrayList<Boolean>();
			for (int i = 0, size = columnNames.size(); i < size; i++) {
				selected.add(true);
			}
			analysis.setSelected(selected);
			long end = System.currentTimeMillis();
			closeConnection();
			System.out.println("*** getHeader  in " + (end-start) + " ms");			
		}
		if (analysis.getSortProperty() == null) {
			List<String> sortProperty = new ArrayList<String>();
			System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$ sort = " + columnNames.get(0));
			sortProperty.add(columnNames.get(0));
			analysis.setSortProperty(sortProperty);
			List<Boolean> ascending = new ArrayList<Boolean>();
			ascending.add(true);
			analysis.setAscending(ascending);
		}
		System.out.println("----------> HEADER : " + analysis.getSelectedColumns());
		return getHeaderColumnNames(analysis.getSelectedColumns());
	}

	@Override
	public Integer getRowCount(Analysis analysis) throws AnalysisException {
		if (analysis == null) {
			return 0;
		}
		if (rowCount == -1) {			
			long start = System.currentTimeMillis();
			initConnection();
			if (db == null) {
				return 0;
			}
			System.out.println("---------- getRowCount");
			String sql = analysis.toSql(false);
			System.out.println("       sql=" + sql);
			
			
			List<ODocument> list = null;
			try {
				list = db.query(new OSQLSynchQuery<ODocument>("SELECT COUNT(*) as count FROM ( " + sql + " )"));
			} catch (Throwable t) {
				// critical case
				// class (table) not found in database				
				recordError(analysis, t);
				return 0;
			}
			int count = ((Long) list.get(0).field("count")).intValue();

			closeConnection();
			long end = System.currentTimeMillis();
			System.out.println("*** count = " + count + "  in " + (end-start) + " ms");
			rowCount = count;
		}
		return rowCount;
	}

	@Override
	public Iterator<AnalysisRow> iterator(Analysis analysis, long first, long count) throws AnalysisException {
		long start = System.currentTimeMillis();		
		System.out.println("---------- iterator");
		List<AnalysisRow> list = new ArrayList<AnalysisRow>();
		if (analysis == null) {
			return list.iterator();
		}
		initConnection();
		if (db == null) {
			return list.iterator();
		}
		String sql = analysis.toSql(false);
		System.out.println("*** SQL = " + sql);

		try {
			OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<ODocument>(sql + " SKIP " + first + " LIMIT " + count);
			List<ODocument> resultset = db.query(query);				
			
			List<String> header = getHeader(analysis);
			int cols = header.size();
			for (ODocument doc : resultset) {				
				//String[] fieldNames = doc.fieldNames(); // this returns only not-null fields!				
				List<Object> cellValues = new ArrayList<Object>();
				for (int i = 1; i <= cols; i++) {
					cellValues.add(doc.field(header.get(i-1)));
				}
				AnalysisRow row = new AnalysisRow(cellValues);
				list.add(row);
			}
		} catch (Throwable t) {			
			recordError(analysis, t);
		}
		
		long end = System.currentTimeMillis();
		closeConnection();
		System.out.println("*** iterator  in " + (end-start) + " ms");

		return list.iterator();

	}	

	@Required
	public void setStorageService(StorageService storageService) {
		this.storageService = storageService;
	}
	
	@Required
	public void setAnalysisService(AnalysisService analysisService) {
		this.analysisService = analysisService;
	}
	
	@Required
	public void setReportService(ReportService reportService) {
		this.reportService = reportService;
	}

	private void initConnection() {
		if (db == null) {
			try {
				db = new ODatabaseDocumentTx(analysisService.getDatabasePath(), false).open("admin", "admin");
			} catch (Throwable t) {
				// critical case when someone deleted the database folder
				t.printStackTrace();
				LOG.error(t.getMessage(), t);
			}
		}
	}
	
	private void closeConnection() {
		if (db != null) {
			db.close();
			db = null;
		}
	}

	private Columns getColumns(Analysis analysis) {
		String sql = analysis.toSql(true);
		System.out.println("*** SQL = " + sql);

		List<String> columnNames = new LinkedList<String>();
		Map<String, String> columnTypes = new HashMap<String, String>();
		try {
			OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<ODocument>(sql + " LIMIT 1");
			List<ODocument> resultset = db.query(query);
			String[] fieldNames = resultset.get(0).fieldNames();			
			int columnCount = fieldNames.length;
			for (int i = 0; i < columnCount; i++) {
				String name = fieldNames[i];				
				OType type = resultset.get(0).getSchemaClass().getProperty(name).getType();
				columnNames.add(name);				
				columnTypes.put(name, DatabaseUtil.getJavaType(name, type));
				System.out.println("************ NAME="+name + "  type="+type + "  javaType="+DatabaseUtil.getJavaType(name, type));
			}
		} catch (Throwable t) {			
			recordError(analysis, t);
		}
		System.out.println("--->  columnTypes=" + columnTypes);

		Columns columns = new Columns();
		columns.setColumnNames(columnNames);
		columns.setColumnTypes(columnTypes);

		return columns;
	}

	public void reset() {
		rowCount = -1;		
	}

	private class Columns {
		private List<String> columnNames;
		private Map<String, String> columnTypes;

		public Columns() {
		}

		public List<String> getColumnNames() {
			return columnNames;
		}

		public void setColumnNames(List<String> columnNames) {
			this.columnNames = columnNames;
		}

		public Map<String, String> getColumnTypes() {
			return columnTypes;
		}

		public void setColumnTypes(Map<String, String> columnTypes) {
			this.columnTypes = columnTypes;
		}

	}

	private List<String> getHeaderColumnNames(List<String> names) {
		List<String> result = new ArrayList<String>();
		for (String name : names) {
			result.add(DatabaseUtil.getColumnAlias(name));
		}
		return result;
	}
	
	private int findIndex(String[] elements, String element) {
		if (elements == null) {
			return -1;
		} else {
			for (int i=0, size=elements.length; i<size; i++) {
				if (elements[i].equals(element)) {
					return i;
				}
			}
		}
		return -1;
	}
	
	private void recordError(Analysis analysis, Throwable t) {
		LOG.error(t.getMessage(), t);
		ReportResultEvent event = new ReportResultEvent(analysis.getCreatedBy(), analysis.getName(), AnalysisUtil.ANY_ACTION,
				AnalysisUtil.ANY_ACTION_FAILED + t.getMessage());
		reportService.notifyReportListener(event);			
	}

}
