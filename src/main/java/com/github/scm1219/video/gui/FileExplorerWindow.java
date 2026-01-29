package com.github.scm1219.video.gui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ScrollPaneLayout;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.apache.commons.lang3.StringUtils;

import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.DiskManager;
import com.github.scm1219.video.gui.table.FileTable;
import com.github.scm1219.video.gui.table.FileTableModel;
import com.github.scm1219.video.gui.tree.FileTree;
import com.github.scm1219.video.gui.tree.FileTreeCellRenderer;
import com.github.scm1219.video.gui.tree.FileTreeNode;
import com.github.scm1219.video.gui.tree.FileUpdateProcesser;
import com.github.scm1219.utils.FileUtils;

import java.awt.Font;

public class FileExplorerWindow extends JFrame {
	private static final long serialVersionUID = 1L;
	private JPanel pnlTop;
    private JPanel pnlLeft;
    private JPanel pnlRight;
    private JPanel pnlIndexInfo;
    private JSplitPane spBottom;
    private JSplitPane spLeft;
    private JTextField tfDir;
    private JTextField tfSearch;
    private JCheckBox chkRealtimeSearch;  // 实时搜索复选框
    private Timer searchTimer;            // 延迟定时器
    private FileTreeNode fileTreeRoot;
    private FileTree trFileTree;
    private FileTable tbFile;
    private Stack<File> stackFile;
    private JButton btnBack;
    private JButton btnSearch;
    private JButton btnCleanSearch;
    private JButton btnSearchDir;
    private JButton btnRefresh;
    private JCheckBox chkVideoOnly;
    private JCheckBox chkShowAllDisks;
    private JScrollPane spTable;
    private JScrollPane spIndexInfo;
    private FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private JPopupMenu menu = new JPopupMenu();
    private JMenuItem mNavigateTo;
    private javax.swing.JLabel lblStatusBar;  // 状态栏标签
    private boolean videoOnly = false;
    private boolean showAllDisks = false;
    private boolean isSearchMode = false;

    private void initData(){
        stackFile = new Stack<File>();
        File[] rootDisks;
        
        if (showAllDisks) {
            rootDisks = File.listRoots();
        } else {
            List<Disk> listDisk = DiskManager.getInstance().listDisk();
            rootDisks = new File[listDisk.size()];
            for (int i = 0; i < rootDisks.length; i++) {
                rootDisks[i]=listDisk.get(i).getRoot();
            }
        }
        
        if(rootDisks.length>0) {
	        fileTreeRoot = new FileTreeNode(rootDisks[0], true);
	        
	        File[] files = rootDisks;
	        for (int i = 0; i < files.length; i++) {
	            if (files[i].isDirectory())
	            {
	                FileTreeNode childNode = new FileTreeNode(files[i], false);
	                Disk disk = DiskManager.getInstance().findDisk(files[i]);
	                childNode.setIndexed(disk != null && disk.needIndex());
	                fileTreeRoot.add(childNode);
	            }
	        }
        }
    }

    private void createComponent(){
        pnlTop = new JPanel();
        pnlLeft = new JPanel();
        pnlRight = new JPanel();
        pnlIndexInfo = new JPanel();
        spBottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        spLeft = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        tfDir = new JTextField();
        tfDir.setEditable(false);
        tfSearch =  new JTextField();
        trFileTree = new FileTree();
        tbFile = new FileTable();
        btnBack = new JButton("后退");
        btnSearch =  new JButton("搜索文件");
        btnCleanSearch =  new JButton("清空搜索");
        btnSearchDir = new JButton("搜索文件夹");
        btnRefresh = new JButton("刷新磁盘");
        chkVideoOnly = new JCheckBox("只显示视频文件");
        chkShowAllDisks = new JCheckBox("显示所有磁盘");
        chkRealtimeSearch = new JCheckBox("实时搜索");
        chkRealtimeSearch.setSelected(false);  // 默认不选中
        lblStatusBar = new javax.swing.JLabel(" ");  // 初始化状态栏

        // 初始化实时搜索定时器（700ms延迟）
        searchTimer = new Timer(700, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (chkRealtimeSearch.isSelected() && StringUtils.isNotBlank(tfSearch.getText())) {
                    performSearch();  // 执行搜索
                }
            }
        });
        searchTimer.setRepeats(false);  // 仅触发一次，不重复

        JMenuItem mEchoIndexInfo;
		mEchoIndexInfo = new JMenuItem("打开所在文件夹");
		menu.add(mEchoIndexInfo);
		mEchoIndexInfo.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				FileTable fileTable = (FileTable)menu.getInvoker();
				int row =fileTable.getSelectedRow();
                File file = (File) fileTable.getValueAt(row, 0);
                String filePath = file.getAbsolutePath();
                if (ClickDebouncer.shouldOpen(filePath)) {
                    try {
                        FileUtils.openDirAndSelectFile(file);
                    } catch (Exception ex) {
                        ClickDebouncer.recordError(filePath);
                    }
                }
			}
		});

		// 新增：扫描此目录菜单项
		JMenuItem mScanDirectory;
		mScanDirectory = new JMenuItem("扫描此目录");
		menu.add(mScanDirectory);
		mScanDirectory.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				FileTable fileTable = (FileTable)menu.getInvoker();
				int row = fileTable.getSelectedRow();
                File file = (File) fileTable.getValueAt(row, 0);

                // 确定要扫描的目录
                File targetDir;
                if(file.isDirectory()) {
                	targetDir = file;
                } else {
                	targetDir = file.getParentFile();
                }

                // 查找磁盘
                Disk disk = DiskManager.getInstance().findDisk(targetDir);
                if(disk == null) {
                	JOptionPane.showMessageDialog(null,
                			"该磁盘未启用索引功能\n请在磁盘根目录创建 " + Disk.FLAF_FILE + " 文件",
                			"提示",
                			MessageType.INFO.ordinal());
                	return;
                }

                // 检查是否正在索引
                if(disk.getIndex().isIndexing()) {
                	JOptionPane.showMessageDialog(null,
                			"索引正在创建中，请稍后",
                			"提示",
                			MessageType.INFO.ordinal());
                	return;
                }

                // 启动目录扫描
                new Thread(new Runnable() {
                	@Override
                	public void run() {
                		FileUpdateProcesser pro = new FileUpdateProcesser(disk, targetDir);
                		pro.setVisible(true);
                	}
                }).start();
			}
		});

		// 新增：转到菜单项
		mNavigateTo = new JMenuItem("转到");
		menu.add(mNavigateTo);
		mNavigateTo.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				FileTable fileTable = (FileTable)menu.getInvoker();
				int row = fileTable.getSelectedRow();
                File file = (File) fileTable.getValueAt(row, 0);

                // 检测文件存在性
                if (canNavigateToFile(file)) {
                    File parentDir = file.getParentFile();
                    updateTable(parentDir, false);
                } else {
                    JOptionPane.showMessageDialog(FileExplorerWindow.this,
                        file == null || !file.exists() ? "文件不存在" : "父目录不存在或无法访问",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                }
			}
		});

		// 创建菜单栏和主题菜单
		createMenuBar();
    }

    /**
     * 创建菜单栏和主题菜单
     */
    private void createMenuBar() {
    	JMenuBar menuBar = new JMenuBar();

    	// 创建主题菜单
    	JMenu themeMenu = new JMenu("主题");

    	// 定义主题配置：显示名称 + 主题代码
    	String[][] themeConfigs = {
    		{"浅色主题", ThemeManager.THEME_LIGHT},
    		{"深色主题", ThemeManager.THEME_DARK},
    		{"跟随系统", ThemeManager.THEME_AUTO}
    	};

    	// 循环创建菜单项
    	for (String[] config : themeConfigs) {
    		JMenuItem item = new JMenuItem(config[0]);
    		final String themeCode = config[1]; // 使用 final 变量供 lambda 使用
    		item.addActionListener(e -> switchTheme(themeCode));
    		themeMenu.add(item);
    	}

    	// 将主题菜单添加到菜单栏
    	menuBar.add(themeMenu);

    	// 设置窗口的菜单栏
    	this.setJMenuBar(menuBar);
    }

    /**
     * 切换主题
     * @param themeName 主题名称（light/dark/auto）
     */
    private void switchTheme(String themeName) {
    	boolean success = ThemeManager.getInstance().applyTheme(themeName);
    	if (success) {
    		// 刷新所有组件的 UI
    		ThemeManager.updateUI();
    		// 显示切换成功提示
    		JOptionPane.showMessageDialog(this,
    			"已切换到" + ThemeManager.getThemeDisplayName(themeName),
    			"主题切换",
    			JOptionPane.INFORMATION_MESSAGE);
    	} else {
    		// 显示切换失败提示
    		JOptionPane.showMessageDialog(this,
    			"主题切换失败，请查看控制台日志",
    			"错误",
    			JOptionPane.ERROR_MESSAGE);
    	}
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

    public static final Comparator<Long> FILE_SIZE_COMPARATOR = new Comparator<Long>() {
        @Override
        public int compare(Long size1, Long size2) {
            return Long.compare(size1, size2);
        }
    };

    /**
     * 执行文件搜索（供按钮、键盘、实时搜索调用）
     */
    private void performSearch() {
        String keyword = tfSearch.getText();
        if (StringUtils.isNotBlank(keyword)) {
            List<File> files = DiskManager.getInstance().searchAllFiles(keyword);
            updateSearchResult(files);
        }
    }

    /**
     * 更新搜索结果
     * @param files
     */
    private void updateSearchResult(List<File> files) {
    	isSearchMode = true;
    	// 统计已删除的文件数量
    	int deletedCount = 0;
    	for (File file : files) {
    		if (!file.exists()) {
    			deletedCount++;
    		}
    	}
    	File[] fileArray = new File[files.size()];
    	files.toArray(fileArray);
    	setFileTable(fileArray);
    	// 搜索后滚动条回到顶部
    	spTable.getViewport().setViewPosition(new Point(0, 0));
    	// 更新状态栏显示搜索结果数量和已删除数量
    	updateStatusBar(files.size(), true, deletedCount);
    }
    
    /**
     * 设置表格里的数据
     * @param files 文件数组
     */
    private void setFileTable(File[] files) {
        setFileTable(files, false);
    }

    /**
     * 设置表格里的数据
     * @param files 文件数组
     * @param showParentRow 是否显示"返回上一级"虚拟行
     */
    private void setFileTable(File[] files, boolean showParentRow) {
    	List<File> fileList = new java.util.ArrayList<File>();
        for (int i = 0; i < files.length; i++){
            if (videoOnly) {
                if (files[i].isDirectory() || FileUtils.isVideoFile(files[i])) {
                    fileList.add(files[i]);
                }
            } else {
                fileList.add(files[i]);
            }
        }
        Collections.sort(fileList, FILE_COMPARATOR);
        Object[][] fileData = new Object[fileList.size()][5];
        for (int i = 0; i < fileData.length; i++){
            for (int j = 0; j < 5; j++){
                fileData[i][j] = fileList.get(i);
            }
        }

        FileTableModel model = new FileTableModel(fileData, showParentRow);
//        model.setCheckFileExists(isSearchMode);
        tbFile.setModel(model);
        TableRowSorter<FileTableModel> sort = new TableRowSorter<>(model);
        sort.setComparator(0, FILE_COMPARATOR);
        sort.setComparator(3, FILE_SIZE_COMPARATOR);
        tbFile.setRowSorter(sort);
        tbFile.getColumnModel().getColumn(0).setPreferredWidth(180);
        tbFile.getColumnModel().getColumn(1).setPreferredWidth(120);
        tbFile.getColumnModel().getColumn(2).setPreferredWidth(80);
        tbFile.getColumnModel().getColumn(3).setPreferredWidth(50);
        tbFile.getColumnModel().getColumn(4).setPreferredWidth(150);
        spTable.getViewport().setBackground(Color.white);
        
        // 更新状态栏显示文件总数
        updateStatusBar(fileList.size(), false, 0);
    }
    
    /**
     * 更新状态栏显示
     * @param count 文件数量
     * @param isSearch 是否为搜索模式
     * @param deletedCount 已删除文件数量（仅搜索模式有效）
     */
    private void updateStatusBar(int count, boolean isSearch, int deletedCount) {
        if (isSearch) {
            if (deletedCount > 0) {
                lblStatusBar.setText("搜索结果: " + count + " 个项目，已删除: " + deletedCount + " 个");
            } else {
                lblStatusBar.setText("搜索结果: " + count + " 个项目");
            }
        } else {
            lblStatusBar.setText("共 " + count + " 个项目");
        }
    }

    private File currentDir = null;
    
    private void updateIndexInfo() {
    	StringBuilder info = new StringBuilder();
    	info.append("<html><body style='padding: 10px; font-family: sans-serif;'>");
    	info.append("<h3 style='margin-top: 0; color: #333;'>索引信息</h3>");
    	
    	List<Disk> disks = DiskManager.getInstance().listDisk();
    	info.append("<p><strong>索引磁盘数量:</strong> ").append(disks.size()).append("</p>");
    	
    	if (!disks.isEmpty()) {
    		info.append("<p><strong>磁盘列表:</strong></p>");
    		info.append("<ul>");
    		for (Disk disk : disks) {
    			String displayName = fileSystemView.getSystemDisplayName(disk.getRoot());
    			if (displayName == null || displayName.isEmpty()) {
    				displayName = disk.getPath();
    			}
    			info.append("<li>").append(displayName).append(" (").append(disk.getPath()).append(")</li>");
    		}
    		info.append("</ul>");
    	}
    	
    	info.append("<p style='color: #666; '>提示: 双击视频文件可直接播放</p>");
    	info.append("</body></html>");
    	
    	pnlIndexInfo.removeAll();
    	javax.swing.JLabel lblInfo = new javax.swing.JLabel(info.toString());
    	lblInfo.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
    	pnlIndexInfo.setLayout(new java.awt.BorderLayout());
    	pnlIndexInfo.add(lblInfo, java.awt.BorderLayout.NORTH);
    	pnlIndexInfo.revalidate();
    	pnlIndexInfo.repaint();
    }

    private void refreshTree(boolean showPrompt) {
    	// 保存当前选中的文件
    	TreePath selectedPath = trFileTree.getSelectionPath();
    	File selectedFile = null;
    	if (selectedPath != null) {
    		FileTreeNode node = (FileTreeNode) selectedPath.getLastPathComponent();
    		selectedFile = node.getFile();
    	}

    	// 执行刷新逻辑
    	DiskManager.getInstance().reloadDisks();
    	initData();

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

        // 恢复选择或选择第一个
        if (selectedFile != null) {
        	restoreSelection(selectedFile);
        } else if (trFileTree.getRowCount() > 0) {
        	trFileTree.setSelectionRow(0);
        }

        updateIndexInfo();

        if (showPrompt) {
        	List<Disk> disks = DiskManager.getInstance().listDisk();
        	if(disks.size()<1) {
        		JOptionPane.showMessageDialog(null, "未发现需要索引的分区\n请在需要索引的分区根目录下\n添加  "+Disk.FLAF_FILE+" 文件", "提示", MessageType.INFO.ordinal());
        	} else {
        		JOptionPane.showMessageDialog(null, "刷新成功，共发现 "+disks.size()+" 个需要索引的磁盘", "提示", MessageType.INFO.ordinal());
        	}
        }
    }

    /**
     * 恢复树的选择到指定的文件
     * @param targetFile 目标文件对象
     */
    private void restoreSelection(File targetFile) {
    	TreeNode root = (TreeNode) trFileTree.getModel().getRoot();

    	// 遍历根节点查找匹配的磁盘
    	for (int i = 0; i < root.getChildCount(); i++) {
    		FileTreeNode child = (FileTreeNode) root.getChildAt(i);
    		if (child.getFile().equals(targetFile)) {
    			// 找到匹配，设置选中路径
    			TreePath path = new TreePath(new Object[]{root, child});
    			trFileTree.setSelectionPath(path);
    			trFileTree.scrollPathToVisible(path);
    			return;
    		}
    	}

    	// 未找到匹配，选择第一个
    	if (trFileTree.getRowCount() > 0) {
    		trFileTree.setSelectionRow(0);
    	}
    }

    /**
     * 判断指定文件是否为磁盘根目录
     * @param file 文件对象
     * @return 如果是磁盘根目录返回 true
     */
    private boolean isDiskRoot(File file) {
        if (file == null || !file.isDirectory()) {
            return false;
        }

        // 检查是否为 File.listRoots() 中的根目录
        File[] roots = File.listRoots();
        for (File root : roots) {
            if (file.equals(root)) {
                return true;
            }
        }

        // 检查是否为索引磁盘列表中的根目录
        List<Disk> disks = DiskManager.getInstance().listDisk();
        for (Disk disk : disks) {
            if (file.equals(disk.getRoot())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 验证文件是否可以跳转到其父目录
     * @param file 要验证的文件
     * @return true 可以跳转，false 不可以
     */
    private boolean canNavigateToFile(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        File parentDir = file.getParentFile();
        return parentDir != null && parentDir.exists();
    }

    private void updateTable(File file, Boolean isBack){
        isSearchMode = false;
        String path = file.getAbsolutePath();
        if (path == null)
            return;
        tfDir.setText(path);
        currentDir = file;
        if (!isBack){
            stackFile.push(file);
        }

        final File targetFile = file;
        
        SwingWorker<File[], Void> worker = new SwingWorker<File[], Void>() {
            @Override
            protected File[] doInBackground() {
                return fileSystemView.getFiles(targetFile, false);
            }

            @Override
            protected void done() {
                try {
                    File[] files = get();
                    // 判断是否在磁盘根目录
                    boolean isRootDirectory = isDiskRoot(targetFile);
                    setFileTable(files, !isRootDirectory);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        worker.execute();
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

                // 检查是否点击虚拟行
                FileTableModel model = fileTable.getFileTableModel();
                if (model != null && model.isParentRow(row)) {
                    // 双击虚拟行触发返回上级
                    if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                        if (stackFile.size() > 1) {
                            stackFile.pop();
                            updateTable(stackFile.peek(), true);
                        }
                    }
                    return;
                }

                File file = (File) fileTable.getValueAt(row, 0);
                if (e.getClickCount() == 1){
//                	TreePath path =trFileTree.getSelectionPath();
//                	path.pathByAddingChild(new FileTreeNode(file));
//                	trFileTree.setSelectionPath(path);
                } else if (e.getClickCount() == 2){
                	if(FileUtils.isVideoFile(file)) {
                		String filePath = file.getAbsolutePath();
                		if (ClickDebouncer.shouldOpen(filePath)) {
                			try {
                				FileUtils.openVideoFile(file);
                			} catch (Exception ex) {
                				ClickDebouncer.recordError(filePath);
                			}
                		}
                	}else if(file.isDirectory()){
                		updateTable(file, false);
                	}
                }
                if(e.getButton() == MouseEvent.BUTTON3) {
                	fileTable.setRowSelectionInterval(row, row);

                	// 动态控制"转到"菜单项状态
                	mNavigateTo.setEnabled(isSearchMode && canNavigateToFile(file));

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
				performSearch();
			}
		});
        
        tfSearch.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					performSearch();
				}
			}
		});

        // 添加搜索框文本变化监听（实时搜索）
        tfSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                restartSearchTimer();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                restartSearchTimer();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // JTextField 使用 PlainDocument，不会触发此事件
                // 保留方法以满足 DocumentListener 接口要求
                restartSearchTimer();
            }

            /**
             * 重新启动搜索定时器（防抖处理）
             */
            private void restartSearchTimer() {
                if (chkRealtimeSearch.isSelected()) {
                    searchTimer.restart();
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

        chkVideoOnly.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                videoOnly = chkVideoOnly.isSelected();
                if (currentDir != null) {
                    updateTable(currentDir, true);
                }
            }
        });

        chkShowAllDisks.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean newShowAllDisks = chkShowAllDisks.isSelected();
                // 仅当状态真正改变时才刷新
                if (newShowAllDisks != showAllDisks) {
                    showAllDisks = newShowAllDisks;
                    refreshTree(false);
                }
            }
        });

        btnRefresh.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshTree(true);
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
        
        spIndexInfo = new JScrollPane(pnlIndexInfo);
        spIndexInfo.setBackground(Color.white);
        spIndexInfo.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        spIndexInfo.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        spIndexInfo.setPreferredSize(new Dimension(200, 150));
        spIndexInfo.setMinimumSize(new Dimension(200, 100));
        
        spLeft.setTopComponent(scroll);
        spLeft.setBottomComponent(spIndexInfo);
        spLeft.setResizeWeight(0.8);

        JPanel pnlLeftTop = new JPanel();
        pnlLeftTop.setLayout(new GridBagLayout());
        pnlLeftTop.add(btnRefresh, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
                GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));
        pnlLeftTop.add(chkShowAllDisks, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
                GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));

        pnlLeft.setLayout(new GridBagLayout());
        pnlLeft.add(pnlLeftTop, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.EAST,
                GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        pnlLeft.add(spLeft, new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

        pnlRight.setLayout(new GridBagLayout());
        pnlRight.add(spTable,new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        pnlRight.add(lblStatusBar, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
                GridBagConstraints.HORIZONTAL, new Insets(2, 5, 2, 5), 0, 0));

        //搜索
        pnlTop.setLayout(new GridBagLayout());
        pnlTop.add(btnSearch, new GridBagConstraints(0, 0, 1, 1, 0.05, 1.0, GridBagConstraints.WEST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        pnlTop.add(btnSearchDir,new GridBagConstraints(1, 0, 1, 1, 0.05, 1.0, GridBagConstraints.WEST,
                GridBagConstraints.BOTH, new Insets(1, 0, 0, 0), 0, 0));
        pnlTop.add(tfSearch,new GridBagConstraints(2, 0, 1, 1, 0.82, 1.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        pnlTop.add(chkRealtimeSearch, new GridBagConstraints(3, 0, 1, 1, 0.13, 1.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(0, 5, 0, 0), 0, 0));
        pnlTop.add(chkVideoOnly, new GridBagConstraints(3, 1, 1, 1, 0.13, 0.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(2, 5, 0, 0), 0, 0));


        //路径
        pnlTop.add(btnBack, new GridBagConstraints(0, 1, 1, 1, 0.05, 1.0, GridBagConstraints.WEST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        pnlTop.add(btnCleanSearch, new GridBagConstraints(1, 1, 1, 1, 0.05, 1.0, GridBagConstraints.WEST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

        pnlTop.add(tfDir,new GridBagConstraints(2, 1, 1, 1, 0.95, 1.0, GridBagConstraints.EAST,
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
        
        updateIndexInfo();

        // 设置搜索框快捷键
        setupSearchShortcuts();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.setBounds(screenSize.width/2 - width/2, screenSize.height/2 - height/2, width, height);
        this.setMinimumSize(new Dimension(width, height));
    }

    /**
     * 设置搜索框的快捷键
     * - Ctrl+F: 定位搜索框并选中全部文本
     * - Alt+W: 清空搜索内容并刷新表格（回到当前目录）
     */
    private void setupSearchShortcuts() {
        // 获取输入映射和动作映射
        InputMap inputMap = tfSearch.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = tfSearch.getActionMap();

        // 注册 Ctrl+F: 定位搜索框并选中全部文本
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "focusSearch");
        actionMap.put("focusSearch", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                tfSearch.requestFocusInWindow();
                tfSearch.selectAll();
            }
        });

        // 注册 Alt+W: 清空搜索内容并刷新表格（与"清空搜索"按钮逻辑一致）
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.ALT_DOWN_MASK), "clearSearch");
        actionMap.put("clearSearch", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                tfSearch.setText("");
                if(currentDir!=null) {
                    updateTable(currentDir,false);
                }
                tfSearch.requestFocusInWindow();  // 保持焦点在搜索框
            }
        });
    }

    /**
     * 激活搜索框焦点（供外部调用）
     * 用于窗口初始化后将焦点定位到搜索框
     */
    public void focusSearchField() {
        tfSearch.requestFocusInWindow();
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
                // 释放定时器资源
                if (searchTimer != null && searchTimer.isRunning()) {
                    searchTimer.stop();
                }
            }
        });
        if(DiskManager.getInstance().listDisk().size()<1) {
        	JOptionPane.showMessageDialog(null, "未发现需要索引的分区\n请在需要索引的分区根目录下\n添加  "+Disk.FLAF_FILE+" 文件", "警告", MessageType.INFO.ordinal());
        }
    }
    
}
