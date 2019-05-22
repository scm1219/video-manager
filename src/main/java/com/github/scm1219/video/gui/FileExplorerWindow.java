package com.github.scm1219.video.gui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ScrollPaneLayout;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultTreeModel;

import org.apache.commons.lang3.StringUtils;

import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.DiskManager;
import com.github.scm1219.video.gui.table.FileTable;
import com.github.scm1219.video.gui.table.FileTableModel;
import com.github.scm1219.video.gui.tree.FileTree;
import com.github.scm1219.video.gui.tree.FileTreeCellRenderer;
import com.github.scm1219.video.gui.tree.FileTreeNode;
import com.github.scm1219.utils.FileUtils;

public class FileExplorerWindow extends JFrame {
	private static final long serialVersionUID = 1L;
	private JPanel pnlTop;
    private JPanel pnlLeft;
    private JPanel pnlRight;
    private JSplitPane spBottom;
    private JTextField tfDir;
    private JTextField tfSearch;
    private FileTreeNode fileTreeRoot;
    private FileTree trFileTree;
    private FileTable tbFile;
    private Stack<File> stackFile;
    private JButton btnBack;
    private JButton btnSearch;
    private JButton btnCleanSearch;
    private JButton btnSearchDir;
    private JScrollPane spTable;
    private FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private JPopupMenu menu = new JPopupMenu();

    private void initData(){
        stackFile = new Stack<File>();
        List<Disk> listDisk = DiskManager.getInstance().listDisk();
        
        File[] rootDisks = new File[listDisk.size()];
        
        if(rootDisks.length>0) {
        	for (int i = 0; i < rootDisks.length; i++) {
    			rootDisks[i]=listDisk.get(i).getRoot();
    		}
	        fileTreeRoot = new FileTreeNode(rootDisks[0], true);
	        
	        File[] files = rootDisks;
	        for (int i = 0; i < files.length; i++) {
	            if (files[i].isDirectory())
	            {
	                FileTreeNode childNode = new FileTreeNode(files[i], false);
	                fileTreeRoot.add(childNode);
	            }
	        }
        }
    }

    private void createComponent(){
        pnlTop = new JPanel();
        pnlLeft = new JPanel();
        pnlRight = new JPanel();
        spBottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        tfDir = new JTextField();
        tfSearch =  new JTextField();
        trFileTree = new FileTree();
        tbFile = new FileTable();
        btnBack = new JButton("后退");
        btnSearch =  new JButton("搜索文件");
        btnCleanSearch =  new JButton("清空搜索");
        btnSearchDir = new JButton("搜索文件夹");
        JMenuItem mEchoIndexInfo;
		mEchoIndexInfo = new JMenuItem("打开所在文件夹");
		menu.add(mEchoIndexInfo);
		mEchoIndexInfo.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				FileTable fileTable = (FileTable)menu.getInvoker();
				int row =fileTable.getSelectedRow();
                File file = (File) fileTable.getValueAt(row, 0);
                FileUtils.openDir(file.getParentFile());
			}
		});
    }

    public static final Comparator<File> FILE_COMPARATOR= new Comparator<File>() {
        @Override
        public int compare(File f1, File f2) {
            if (f1.isDirectory()){
                if (f2.isDirectory()){
                    return f1.getName().compareToIgnoreCase(f2.getName());
                } else {
                    return -1;
                }
            } else {
                if (!f2.isDirectory()){
                    return f1.getName().compareToIgnoreCase(f2.getName());
                } else {
                    return 1;
                }
            }
        }
    };
    
    /**
     * 更新搜索结果
     * @param files
     */
    private void updateSearchResult(List<File> files) {
    	File[] fileArray = new File[files.size()];
    	files.toArray(fileArray);
    	setFileTable(fileArray);
    }
    
    /**
     * 设置表格里的数据
     * @param files
     */
    private void setFileTable(File[] files) {
    	Vector<File> vFile = new Vector<File>();
        for (int i = 0; i < files.length; i++){
            vFile.add(files[i]);
        }
        Collections.sort(vFile, FILE_COMPARATOR);
        Object[][] fileData;
        fileData = new Object[files.length][5];
        for (int i = 0; i < fileData.length; i++){
            for (int j = 0; j < 5; j++){
                fileData[i][j] = vFile.elementAt(i);
            }
        }

        FileTableModel model = new FileTableModel(fileData);
        tbFile.setModel(model);
        TableRowSorter<FileTableModel> sort = new TableRowSorter<>(model);
        sort.setComparator(0, FILE_COMPARATOR);
        tbFile.setRowSorter(sort);
        // 设置table 列宽
        tbFile.getColumnModel().getColumn(0).setPreferredWidth(180);
        tbFile.getColumnModel().getColumn(1).setPreferredWidth(120);
        tbFile.getColumnModel().getColumn(2).setPreferredWidth(80);
        tbFile.getColumnModel().getColumn(3).setPreferredWidth(50);
        tbFile.getColumnModel().getColumn(4).setPreferredWidth(150);
        spTable.getViewport().setBackground(Color.white);
    }

    private File currentDir = null;
    private void updateTable(File file, Boolean isBack){
        String path = file.getAbsolutePath();
        if (path == null)
            return;
        tfDir.setText(path);
        currentDir = file;
        if (!isBack){
            stackFile.push(file);
        }
        File[] files = fileSystemView.getFiles(file, false);
        setFileTable(files);
    }

    private void AddComponentListener(){
        trFileTree.addTreeSelectionListener(new TreeSelectionListener(){
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                JTree tree = (JTree)e.getSource();
                FileTreeNode selectNode = (FileTreeNode)tree.getLastSelectedPathComponent();
                if (selectNode != null){
                    File file = selectNode.getFile();
                    updateTable(file, false);
                }
            }
        });

        trFileTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1)
                {
                    JTree tree = (JTree)e.getSource();
                    FileTreeNode selectNode = (FileTreeNode)tree.getLastSelectedPathComponent();
                    if (selectNode != null){
                        File file = selectNode.getFile();
                        updateTable(file, false);
                    }
                }
            }
        });

        tbFile.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                FileTable fileTable = (FileTable)e.getSource();
                int row = fileTable.rowAtPoint(e.getPoint());
                File file = (File) fileTable.getValueAt(row, 0);
                if (e.getClickCount() == 1){
//                	TreePath path =trFileTree.getSelectionPath();
//                	path.pathByAddingChild(new FileTreeNode(file));
//                	trFileTree.setSelectionPath(path);
                } else if (e.getClickCount() == 2){
                	if(FileUtils.isVideoFile(file)) {
                		FileUtils.openVideoFile(file);
                	}else if(file.isDirectory()){
                		updateTable(file, false);
                	}
                }
                if(e.getButton() == MouseEvent.BUTTON3) {
                	menu.show(tbFile, e.getX(), e.getY());
                }
            }
        });

        btnBack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (stackFile.size()>1) {
                    stackFile.pop();
                    updateTable(stackFile.peek(), true);
                }
            }
        });
        btnCleanSearch.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				tfSearch.setText("");
				if(currentDir!=null) {
					updateTable(currentDir,false);
				}
			}
		});
        btnSearch.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String test = tfSearch.getText();
				if(StringUtils.isNotBlank(test)) {
					List<File> files = DiskManager.getInstance().searchAllFiles(test);
					updateSearchResult(files);
				}
			}
		});

        btnSearchDir.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				String test = tfSearch.getText();
				if(StringUtils.isNotBlank(test)) {
					List<File> files = DiskManager.getInstance().searchAllDirs(test);
					updateSearchResult(files);
				}
			}
		});
    }

    private void initComponent(){
        initData();
        createComponent();
        AddComponentListener();

        spTable = new JScrollPane(tbFile);
        spTable.setBackground(Color.white);
        // 设置table 列宽
        DefaultTreeModel dfTreeModel = new DefaultTreeModel(fileTreeRoot) {
			private static final long serialVersionUID = 1L;

			@Override
            public boolean isLeaf(Object node) {
                FileTreeNode treeNode = (FileTreeNode) node;
                if (treeNode.isDummyRoot()){
                    return false;
                }
                return ((File)treeNode.getUserObject()).isFile();
            }
        };
        trFileTree.setModel(dfTreeModel);
        trFileTree.setCellRenderer(new FileTreeCellRenderer());
        trFileTree.setRootVisible(false);
        trFileTree.setSelectionRow(0);

        JScrollPane scroll = new JScrollPane(trFileTree);
        scroll.setLayout(new ScrollPaneLayout());
        scroll.getViewport().setBackground(Color.WHITE);
        pnlLeft.setLayout(new GridBagLayout());
        pnlLeft.add(scroll, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

        pnlRight.setLayout(new GridBagLayout());
        pnlRight.add(spTable,new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

        //搜索
        pnlTop.setLayout(new GridBagLayout());
        pnlTop.add(btnSearch, new GridBagConstraints(0, 0, 1, 1, 0.05, 1.0, GridBagConstraints.WEST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        pnlTop.add(btnSearchDir,new GridBagConstraints(1, 0, 1, 1, 0.05, 1.0, GridBagConstraints.WEST,
                GridBagConstraints.BOTH, new Insets(1, 0, 0, 0), 0, 0));
        pnlTop.add(tfSearch,new GridBagConstraints(2, 0, 1, 1, 0.9, 1.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        
        
        //路径
        pnlTop.add(btnBack, new GridBagConstraints(0, 1, 1, 1, 0.05, 1.0, GridBagConstraints.WEST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        pnlTop.add(btnCleanSearch, new GridBagConstraints(1, 1, 1, 1, 0.05, 1.0, GridBagConstraints.WEST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        
        pnlTop.add(tfDir,new GridBagConstraints(2, 1, 1, 1, 0.9, 1.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

        spBottom.setLeftComponent(pnlLeft);
        spBottom.setRightComponent(pnlRight);
        pnlLeft.setMinimumSize(new Dimension(200,height));

        Container container = this.getContentPane();
        container.setLayout(new GridBagLayout());
        container.add(pnlTop, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.05, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(2, 0, 0, 0), 0, 0));
        container.add(spBottom, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.95, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(4, 2, 2, 2), 0, 0));
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.setBounds(screenSize.width/2 - width/2, screenSize.height/2 - height/2, width, height);
        this.setMinimumSize(new Dimension(width, height));
    }

    private int width=1024;
    private int height=768;
    public FileExplorerWindow(){
        super("视频文件浏览器");
        initComponent();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                super.windowClosed(e);
            }
        });
        if(DiskManager.getInstance().listDisk().size()<1) {
        	JOptionPane.showMessageDialog(null, "未发现需要索引的分区\n请在需要索引的分区根目录下\n添加  "+Disk.FLAF_FILE+" 文件", "警告", MessageType.INFO.ordinal());
        }
    }
    
}
