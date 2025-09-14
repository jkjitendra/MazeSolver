package com.drostwades.mazesolver;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.math.BigInteger;
import java.util.*;
import java.util.List;

/**
 * Solution for MathMaze problems provided on Matiks website:
 * <a href="https://www.matiks.in/puzzle/daily-challenge?puzzleType=MathMaze">Matiks MathMaze Puzzle</a>
 *
 * Interactive UI solver:
 * - User sets n (<= 15), clicks Generate to (re)create the n x n grid.
 * - Snake traversal: up/down/left/right, no revisits.
 * - Alternate number → operator → number → ...
 * - Immediate evaluation with exact rational arithmetic (Rational).
 * - Finds up to MAX_SOLUTIONS within TIME_LIMIT_MS.
 * - Prints all solutions and one shortest path at the end.
 *
 * UX:
 * - Tab moves focus and auto-starts editing (white background, caret visible).
 * - Clear buttons per row (left) and a Clear Grid button.
 * - Solutions list (left) + Visual grid preview (right) showing the highlighted path.
 */
public class MazeSolverUI extends JFrame {

    // --------- Defaults / Tuning ----------
    private static final int DEFAULT_MAX_SOLUTIONS = 20;
    private static final int DEFAULT_TIME_LIMIT_MS = 5000;
    // --------------------------------------

    private JSpinner nSpinner;
    private JTextField targetField;
    private JSpinner maxSolutionsSpinner;
    private JSpinner timeLimitSpinner;

    private EditableTable gridTable;
    private JScrollPane gridScroll;
    private RowHeader rowHeader;


    // Solutions & preview
    private DefaultListModel<Solution> solutionsModel;
    private JList<Solution> solutionsList;
    private GridPreviewPanel previewPanel;
    private JButton showShortestBtn;

    // Controls
    private JButton generateButton;
    private JButton solveButton;
    private JButton sampleButton;
    private JButton clearGridButton;

    // --- Preview control fields (promote from locals) ---
    private JButton playBtn;
    private JButton pauseBtn;
    private JButton resetBtn;
    private JCheckBox loopChk;
    private JSlider speedSlider;
    private Runnable syncButtons;

    public MazeSolverUI(int initialN) {
        super("MathMaze Solver (Matiks) — Exact Rational");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));

        // ---------- TOP: controls ----------
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 10, 6, 10);
        gc.fill = GridBagConstraints.HORIZONTAL;

        nSpinner = new JSpinner(new SpinnerNumberModel(initialN, 2, 15, 1));
        nSpinner.setPreferredSize(new Dimension(70, 28));
        nSpinner.setToolTipText("Grid size n (2–15)");

        targetField = new JTextField("1", 10);
        targetField.setPreferredSize(new Dimension(110, 28));
        targetField.setToolTipText("Target (integer or terminating decimal)");

        maxSolutionsSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_MAX_SOLUTIONS, 1, 1000, 1));
        maxSolutionsSpinner.setPreferredSize(new Dimension(110, 28));
        maxSolutionsSpinner.setToolTipText("Maximum solutions to collect");

        timeLimitSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_TIME_LIMIT_MS, 100, 600000, 100));
        timeLimitSpinner.setPreferredSize(new Dimension(120, 28));
        timeLimitSpinner.setToolTipText("Time limit in milliseconds");

        int row = 0;
        gc.gridy = row;

        gc.gridx = 0; top.add(new JLabel("Grid size (n ≤ 15):"), gc);
        gc.gridx = 1; top.add(nSpinner, gc);

        gc.gridx = 2; top.add(new JLabel("Target:"), gc);
        gc.gridx = 3; top.add(targetField, gc);

        gc.gridx = 4; top.add(new JLabel("Max solutions:"), gc);
        gc.gridx = 5; top.add(maxSolutionsSpinner, gc);

        gc.gridx = 6; top.add(new JLabel("Time limit (ms):"), gc);
        gc.gridx = 7; top.add(timeLimitSpinner, gc);

        row++;
        gc.gridy = row;
        gc.insets = new Insets(0, 10, 10, 10);

        generateButton = new JButton("Generate");
        generateButton.setToolTipText("Apply n and (re)create an empty n×n grid");

        solveButton = new JButton("Submit & Solve");
        solveButton.setToolTipText("Use current grid + target to solve");

        sampleButton = new JButton("Load Sample");
        sampleButton.setToolTipText("Load a sample grid (resizes table)");

        clearGridButton = new JButton("Clear Grid");
        clearGridButton.setToolTipText("Clear all values (keep grid size)");

        showShortestBtn = new JButton("Show Shortest");
        showShortestBtn.setToolTipText("Select the shortest path in the solutions list");

        gc.gridx = 0; gc.gridwidth = 1; top.add(generateButton, gc);
        gc.gridx = 1; gc.gridwidth = 1; top.add(showShortestBtn, gc);
        gc.gridx = 2; gc.gridwidth = 2; top.add(solveButton, gc);
        gc.gridx = 4; gc.gridwidth = 2; top.add(sampleButton, gc);
        gc.gridx = 6; gc.gridwidth = 2; top.add(clearGridButton, gc);

        add(top, BorderLayout.NORTH);

        // ---------- CENTER: editable grid + row clear buttons ----------
        JPanel center = new JPanel(new BorderLayout(8, 8));

        gridTable = new EditableTable(makeModel(initialN));
        configureTableUX(gridTable);

        gridScroll = new JScrollPane(gridTable);
        gridScroll.setPreferredSize(new Dimension(900, 360));

        // Row header that shows "Clear row i" aligned to each row
        rowHeader = new RowHeader(gridTable, this::clearRow);
        gridScroll.setRowHeaderView(rowHeader);

        center.add(gridScroll, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        // ---------- SOUTH: Solutions list (left) + Visual preview (right) ----------
        solutionsModel = new DefaultListModel<>();
        solutionsList = new JList<>(solutionsModel);
        solutionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        solutionsList.setVisibleRowCount(12);
        solutionsList.setFixedCellHeight(24);
        solutionsList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane listScroll = new JScrollPane(solutionsList);
        listScroll.setPreferredSize(new Dimension(420, 240));
        listScroll.setBorder(BorderFactory.createTitledBorder("Solutions"));

        previewPanel = new GridPreviewPanel();
        JScrollPane previewScroll = new JScrollPane(previewPanel);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Path Preview"));

        JPanel previewBox = new JPanel(new BorderLayout(0, 6));
        previewBox.add(previewScroll, BorderLayout.CENTER);

        // ---- Controls: Play / Pause / Reset / Loop + speed ----
        JPanel previewControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        playBtn   = new JButton("Play");
        pauseBtn  = new JButton("Pause");
        resetBtn  = new JButton("Reset");
        loopChk   = new JCheckBox("Loop");

        speedSlider = new JSlider(60, 600, 220);
        speedSlider.setToolTipText("Animation speed (ms per step)");
        speedSlider.setPreferredSize(new Dimension(160, 24));

        previewControls.add(playBtn);
        previewControls.add(pauseBtn);
        previewControls.add(resetBtn);
        previewControls.add(loopChk);
        previewControls.add(new JLabel("Speed:"));
        previewControls.add(speedSlider);
        previewBox.add(previewControls, BorderLayout.SOUTH);

        // Left: solutions list, Right: preview (canvas + controls)
        JSplitPane bottomSplit =
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, previewBox);
        bottomSplit.setResizeWeight(0.35);  // keep list ~35% width
        add(bottomSplit, BorderLayout.SOUTH);

        // Button wiring
        syncButtons = () -> {
            boolean hasPath = solutionsList.getSelectedValue() != null;
            playBtn.setEnabled(hasPath && !previewPanel.isPlaying());
            pauseBtn.setEnabled(hasPath && (previewPanel.isPlaying() || previewPanel.isPaused()));
            resetBtn.setEnabled(hasPath);
        };

        playBtn.addActionListener(ev -> {
            Solution sel = solutionsList.getSelectedValue();
            if (sel == null) return;
            previewPanel.setData(getGridSafely(), sel.path);
            previewPanel.play(speedSlider.getValue(), loopChk.isSelected());
            syncButtons.run();
        });

        pauseBtn.addActionListener(ev -> {
            if (previewPanel.isPlaying()) previewPanel.pause();
            else if (previewPanel.isPaused()) previewPanel.resume();
            syncButtons.run();
        });

        resetBtn.addActionListener(ev -> {
            previewPanel.reset();
            syncButtons.run();
        });

        speedSlider.addChangeListener(e -> {
            previewPanel.setSpeed(speedSlider.getValue());
        });

        loopChk.addActionListener(e -> previewPanel.setLoop(loopChk.isSelected()));

        // Wire listeners
        generateButton.addActionListener(ev -> {
            int n = (int) nSpinner.getValue();
            resizeTable(n);
            rowHeader.syncToTable();   // keep header in sync with row height/count
            clearSolutions();
            previewPanel.setData(getGridSafely(), null);
            previewPanel.revalidate(); previewPanel.repaint();
            focusTopLeft();
        });
        sampleButton.addActionListener(this::onLoadSample);
        solveButton.addActionListener(this::onSolve);
        clearGridButton.addActionListener(ev -> {
            clearGrid();
            clearSolutions();
            previewPanel.setData(getGridSafely(), null);
            previewPanel.repaint();
            focusTopLeft();
        });
        showShortestBtn.addActionListener(ev -> selectShortestInList());

        solutionsList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            previewPanel.stopAnimation();                    // stop any running animation
            Solution sel = solutionsList.getSelectedValue();
            previewPanel.setData(getGridSafely(), sel == null ? null : sel.path);
            previewPanel.repaint();

            // make sure the controls reflect a fresh, stopped state for the new selection
            pauseBtn.setText("Pause");
            syncButtons.run();                               // enables Play, disables Pause, enables Reset
        });

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ---- Startup: ask user for n, then build UI ----
    public static void main(String[] args) {

        // Normal interactive mode:
        SwingUtilities.invokeLater(() -> {
            Integer n = promptForN();
            if (n == null) return; // user cancelled
            new MazeSolverUI(n);
        });
    }

    private static Integer promptForN() {
        while (true) {
            String s = JOptionPane.showInputDialog(
                    null,
                    "Enter grid size n (2–15):",
                    "MathMaze — Set Grid Size",
                    JOptionPane.QUESTION_MESSAGE
            );
            if (s == null) return null; // cancel
            s = s.trim();
            if (s.isEmpty()) continue;
            try {
                int n = Integer.parseInt(s);
                if (n >= 2 && n <= 15) return n;
            } catch (NumberFormatException ignored) { }
            JOptionPane.showMessageDialog(null, "Please enter an integer between 2 and 15.", "Invalid n", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ---------- Actions ----------

    private void onLoadSample(ActionEvent e) {
        int n = (int) nSpinner.getValue();
        String[][] sample = generateSample(n);
        resizeTable(n);
        rowHeader.syncToTable();
        setGrid(sample);
        clearSolutions();
        previewPanel.setData(getGridSafely(), null);
        previewPanel.repaint();
        focusTopLeft();
    }

    private void onSolve(ActionEvent e) {
        clearSolutions();
        String[][] grid = getGridSafely();
        if (grid == null) {
            toast("Grid read failed. Fill all cells with digits (0–9) or operators (+-*/).");
            return;
        }
        int n = grid.length;

        if (!isNumber(grid[0][0])) {
            toast("Top-left cell must be a number.");
            return;
        }
        if (!isNumber(grid[n - 1][n - 1])) {
            toast("Bottom-right cell must be a number.");
            return;
        }

        Rational target;
        try {
            target = Rational.parse(targetField.getText().trim());
        } catch (Exception ex) {
            toast("Invalid target: " + ex.getMessage());
            return;
        }

        int maxSolutions = (int) maxSolutionsSpinner.getValue();
        long timeLimitMs = (int) timeLimitSpinner.getValue();

        List<List<int[]>> sols = new ArrayList<>();
        boolean[][] visited = new boolean[n][n];
        List<int[]> path = new ArrayList<>();

        visited[0][0] = true;
        path.add(new int[]{0, 0});
        Rational startVal = Rational.ofInt(Integer.parseInt(grid[0][0]));

        long start = System.currentTimeMillis();
        dfs(grid, 0, 0, startVal, null, true, visited, path, sols, target, maxSolutions, timeLimitMs, start);

        if (System.currentTimeMillis() - start >= timeLimitMs) {
            toast("(Stopped due to time limit)");
        } else if (sols.size() >= maxSolutions) {
            toast("(Stopped after reaching max solutions)");
        }

        // Fill solutions list
        for (List<int[]> s : sols) {
            String expr = buildExpression(grid, s);
            String steps = formatSteps(grid, s);
            solutionsModel.addElement(new Solution(expr, s, steps));
        }

        if (solutionsModel.isEmpty()) {
            toast("No solutions found.");
            previewPanel.setData(grid, null);
        } else {
            // default select first; also set preview
            solutionsList.setSelectedIndex(0);
            Solution sel = solutionsModel.get(0);
            previewPanel.setData(grid, sel.path);
            // ensure buttons show Play (not paused) after auto-selection
            pauseBtn.setText("Pause");
            syncButtons.run();
        }
        previewPanel.revalidate(); previewPanel.repaint();
    }

    private void selectShortestInList() {
        if (solutionsModel.isEmpty()) return;
        int bestIdx = 0;
        int bestLen = solutionsModel.get(0).path.size();
        for (int i = 1; i < solutionsModel.size(); i++) {
            int len = solutionsModel.get(i).path.size();
            if (len < bestLen) { bestLen = len; bestIdx = i; }
        }
        solutionsList.setSelectedIndex(bestIdx);
        solutionsList.ensureIndexIsVisible(bestIdx);
    }

    // ----- DFS solver (snake rule, exact arithmetic) -----
    private void dfs(String[][] grid, int r, int c,
                     Rational currentValue, String pendingOp,
                     boolean expectOperator,
                     boolean[][] visited, List<int[]> path,
                     List<List<int[]>> solutions, Rational target,
                     int maxSolutions, long timeLimitMs, long startTime) {

        if (solutions.size() >= maxSolutions) return;
        if (System.currentTimeMillis() - startTime >= timeLimitMs) return;

        int n = grid.length;

        // Base case
        if (r == n - 1 && c == n - 1 && expectOperator && currentValue.equals(target)) {
            solutions.add(new ArrayList<>(path));
            return;
        }

        final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : DIRS) {
            int nr = r + d[0], nc = c + d[1];
            if (!inBounds(n, nr, nc) || visited[nr][nc]) continue;

            String cell = grid[nr][nc];

            if (expectOperator) {
                if (isOperator(cell)) {
                    visited[nr][nc] = true;
                    path.add(new int[]{nr, nc});
                    dfs(grid, nr, nc, currentValue, cell, false, visited, path, solutions, target, maxSolutions, timeLimitMs, startTime);
                    path.remove(path.size() - 1);
                    visited[nr][nc] = false;
                }
            } else {
                if (isNumber(cell)) {
                    int valInt = Integer.parseInt(cell);
                    Rational val = Rational.ofInt(valInt);

                    Rational newValue;
                    if (pendingOp != null) {
                        try {
                            newValue = apply(currentValue, pendingOp, val);
                        } catch (ArithmeticException ex) {
                            continue; // div by zero etc.
                        }
                    } else {
                        newValue = val;
                    }

                    visited[nr][nc] = true;
                    path.add(new int[]{nr, nc});
                    dfs(grid, nr, nc, newValue, null, true, visited, path, solutions, target, maxSolutions, timeLimitMs, startTime);
                    path.remove(path.size() - 1);
                    visited[nr][nc] = false;
                }
            }
        }
    }

    // ---------- Table & UI helpers ----------

    private DefaultTableModel makeModel(int n) {
        String[] cols = new String[n];
        for (int i = 0; i < n; i++) cols[i] = Integer.toString(i);
        DefaultTableModel model = new DefaultTableModel(cols, n) {
            @Override public boolean isCellEditable(int row, int column) { return true; }
        };
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                model.setValueAt("", r, c);
        return model;
    }

    private void resizeTable(int n) {
        gridTable.setModel(makeModel(n));
        gridTable.setRowHeight(24);
        configureTableUX(gridTable);
        gridScroll.revalidate();
        gridScroll.repaint();
    }

    private void configureTableUX(EditableTable table) {
        table.setCellSelectionEnabled(true);
        table.setSelectionBackground(new Color(230, 230, 230));
        table.setSelectionForeground(Color.BLACK);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setSurrendersFocusOnKeystroke(true);
        table.setShowGrid(true);
        table.setGridColor(new Color(210, 210, 210));
        table.setIntercellSpacing(new Dimension(1, 1));

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                if (hasFocus) {
                    c.setBackground(Color.WHITE);
                    c.setForeground(Color.BLACK);
                } else if (isSelected) {
                    c.setBackground(new Color(230, 230, 230));
                    c.setForeground(Color.BLACK);
                } else {
                    c.setBackground(Color.WHITE);
                    c.setForeground(Color.BLACK);
                }
                // add a subtle border on every cell
                ((JComponent) c).setBorder(BorderFactory.createMatteBorder(1, 1, 0, 0, new Color(225, 225, 225)));
                return c;
            }
        });
    }

    private void clearRow(int r) {
        int cols = gridTable.getColumnCount();
        for (int c = 0; c < cols; c++) gridTable.setValueAt("", r, c);
        gridTable.changeSelection(r, 0, false, false);
        gridTable.requestFocusInWindow();
    }

    private void clearGrid() {
        int rows = gridTable.getRowCount();
        int cols = gridTable.getColumnCount();
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                gridTable.setValueAt("", r, c);
        gridTable.changeSelection(0, 0, false, false);
        gridTable.requestFocusInWindow();
    }

    private void focusTopLeft() {
        if (gridTable.getRowCount() > 0 && gridTable.getColumnCount() > 0) {
            gridTable.changeSelection(0, 0, false, false);
            gridTable.requestFocusInWindow();
        }
    }

    private void clearSolutions() {
        solutionsModel.clear();
        previewPanel.setData(getGridSafely(), null);
        if (pauseBtn != null) pauseBtn.setText("Pause");
        if (syncButtons != null) syncButtons.run();
        previewPanel.repaint();
    }

    // ---------- Preview panel (clear styling + play/pause/reset/loop) ----------
    static class GridPreviewPanel extends JPanel {
        private String[][] grid;          // may be null
        private List<int[]> fullPath;     // complete path (may be null)
        private int visibleSteps = 0;     // how many nodes of the path are shown
        private javax.swing.Timer timer;
        private int delayMs = 220;
        private boolean loop = false;
        private enum State { STOPPED, PLAYING, PAUSED }
        private State state = State.STOPPED;

        // Colors
        private final Color gridLine   = new Color(190, 190, 190);
        private final Color cellText   = new Color(25, 25, 25);
        private final Color visitFill  = new Color(232, 240, 254); // soft blue
        private final Color edgeColor  = new Color(33, 150, 243);  // material blue 500
        private final Color startColor = new Color(46, 125, 50);   // green 700
        private final Color endColor   = new Color(211, 47, 47);   // red 700
        private final Stroke edgeStroke = new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        // ---- API for the outside ----
        void setData(String[][] grid, List<int[]> path) {
            stopTimer();
            this.grid = grid;
            this.fullPath = path;
            this.visibleSteps = (path == null) ? 0 : path.size();
            this.state = (path == null) ? State.STOPPED : State.STOPPED;
            revalidate(); repaint();
        }
        void play(int delayMs, boolean loop) {
            if (grid == null || fullPath == null || fullPath.isEmpty()) return;
            this.delayMs = Math.max(60, delayMs);
            this.loop = loop;
            stopTimer();
            visibleSteps = 1;
            timer = new javax.swing.Timer(this.delayMs, e -> advance());
            timer.start();
            state = State.PLAYING;
        }
        void pause() {
            if (timer != null && state == State.PLAYING) {
                timer.stop();
                state = State.PAUSED;
            }
        }
        void resume() {
            if (timer != null && state == State.PAUSED) {
                timer.start();
                state = State.PLAYING;
            }
        }
        void reset() {
            stopTimer();
            if (fullPath != null) visibleSteps = 1; else visibleSteps = 0;
            state = (fullPath == null) ? State.STOPPED : State.STOPPED;
            repaint();
        }
        void stopAnimation() { stopTimer(); state = State.STOPPED; repaint(); }

        boolean isPlaying() { return state == State.PLAYING; }
        boolean isPaused()  { return state == State.PAUSED; }
        void setLoop(boolean loop) { this.loop = loop; }
        void setSpeed(int delayMs) {
            this.delayMs = Math.max(60, delayMs);
            if (timer != null && state == State.PLAYING) {
                timer.setDelay(this.delayMs);
            }
        }

        private void stopTimer() {
            if (timer != null) { timer.stop(); timer = null; }
        }
        private void advance() {
            if (fullPath == null) return;
            visibleSteps++;
            if (visibleSteps >= fullPath.size()) {
                if (loop) {
                    visibleSteps = 1; // restart
                } else {
                    visibleSteps = fullPath.size();
                    stopTimer();
                    state = State.STOPPED;
                }
            }
            repaint();
        }

        @Override public Dimension getPreferredSize() {
            int cells = (grid == null) ? 6 : grid.length;
            int cell = 44;
            int w = cells * cell + 24;
            int h = cells * cell + 24;
            return new Dimension(Math.max(360, w), Math.max(360, h));
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (grid == null) {
                g2.setColor(new Color(120,120,120));
                g2.drawString("No grid", 12, 20);
                g2.dispose();
                return;
            }

            int n = grid.length;
            int cell = Math.max(30, Math.min(64, Math.min((getWidth()-24)/n, (getHeight()-24)/n)));
            int x0 = (getWidth() - n*cell) / 2;
            int y0 = (getHeight() - n*cell) / 2;

            // Background board
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(x0-8, y0-8, n*cell+16, n*cell+16, 12, 12);

            // Step index map (only visible part)
            int[][] order = null;
            if (fullPath != null) {
                order = new int[n][n];
                for (int[] row : order) Arrays.fill(row, -1);
                for (int i = 0; i < Math.min(visibleSteps, fullPath.size()); i++) {
                    int r = fullPath.get(i)[0], c = fullPath.get(i)[1];
                    if (0<=r && r<n && 0<=c && c<n) order[r][c] = i;
                }
            }

            // Cell fills (visited)
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    int x = x0 + c*cell, y = y0 + r*cell;
                    if (order != null && order[r][c] >= 0) {
                        g2.setColor(visitFill);
                        g2.fillRect(x, y, cell, cell);
                    } else {
                        g2.setColor(Color.WHITE);
                        g2.fillRect(x, y, cell, cell);
                    }
                }
            }

            // Grid lines
            g2.setColor(gridLine);
            for (int i = 0; i <= n; i++) {
                int y = y0 + i*cell;
                g2.drawLine(x0, y, x0 + n*cell, y);
            }
            for (int i = 0; i <= n; i++) {
                int x = x0 + i*cell;
                g2.drawLine(x, y0, x, y0 + n*cell);
            }

            // Cell text
            Font f = getFont().deriveFont(Font.BOLD, Math.max(14f, cell * 0.44f));
            g2.setFont(f);
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    String s = grid[r][c] == null ? "" : grid[r][c];
                    int x = x0 + c*cell, y = y0 + r*cell;
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = x + (cell - fm.stringWidth(s))/2;
                    int ty = y + (cell + fm.getAscent() - fm.getDescent())/2;
                    g2.setColor(cellText);
                    g2.drawString(s, tx, ty);
                }
            }

            // Edges with arrows (limited by visibleSteps)
            if (fullPath != null && visibleSteps >= 2) {
                g2.setColor(edgeColor);
                g2.setStroke(edgeStroke);

                for (int i = 0; i < visibleSteps-1; i++) {
                    int[] a = fullPath.get(i);
                    int[] b = fullPath.get(i+1);
                    int ax = x0 + a[1]*cell + cell/2;
                    int ay = y0 + a[0]*cell + cell/2;
                    int bx = x0 + b[1]*cell + cell/2;
                    int by = y0 + b[0]*cell + cell/2;

                    // main segment
                    g2.drawLine(ax, ay, bx, by);

                    // arrowhead at B
                    drawArrowHead(g2, ax, ay, bx, by, 9, 10);
                }
            }

            // Start/End badges + order dots
            if (order != null) {
                Font small = getFont().deriveFont(Font.PLAIN, Math.max(10f, cell * 0.28f));
                for (int r = 0; r < n; r++) {
                    for (int c = 0; c < n; c++) {
                        int idx = order[r][c];
                        if (idx < 0) continue;

                        int cx = x0 + c*cell + cell/2;
                        int cy = y0 + r*cell + cell/2;

                        if (idx == 0 || (fullPath != null && idx == Math.min(visibleSteps, fullPath.size())-1)) {
                            g2.setStroke(new BasicStroke(3f));
                            g2.setColor(idx == 0 ? startColor : endColor);
                            g2.drawOval(cx - cell/2 + 4, cy - cell/2 + 4, cell-8, cell-8);
                        }

                        // small index dot (top-left)
                        int dotX = x0 + c*cell + 6;
                        int dotY = y0 + r*cell + 6;
                        g2.setColor(new Color(255,255,255,230));
                        g2.fillOval(dotX, dotY, 16, 16);
                        g2.setColor(edgeColor.darker());
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.drawOval(dotX, dotY, 16, 16);
                        g2.setFont(small);
                        String nstr = Integer.toString(idx+1);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.setColor(new Color(45,45,45));
                        g2.drawString(nstr, dotX + (16 - fm.stringWidth(nstr))/2, dotY + 11);
                    }
                }
            }

            g2.dispose();
        }

        private void drawArrowHead(Graphics2D g2, int x0, int y0, int x1, int y1, int size, int spread) {
            double dx = x1 - x0, dy = y1 - y0;
            double ang = Math.atan2(dy, dx);
            int xA = (int) (x1 - size * Math.cos(ang - Math.toRadians(spread)));
            int yA = (int) (y1 - size * Math.sin(ang - Math.toRadians(spread)));
            int xB = (int) (x1 - size * Math.cos(ang + Math.toRadians(spread)));
            int yB = (int) (y1 - size * Math.sin(ang + Math.toRadians(spread)));
            Polygon p = new Polygon();
            p.addPoint(x1, y1);
            p.addPoint(xA, yA);
            p.addPoint(xB, yB);
            g2.fill(p);
        }
    }

    // ---------- Data models ----------

    /** Model item for the solutions JList. */
    static class Solution {
        final String expression;
        final List<int[]> path;
        final String steps;
        Solution(String expression, List<int[]> path, String steps) {
            this.expression = expression;
            this.path = path;
            this.steps = steps;
        }
        @Override public String toString() {
            // Show expression and length for quick scanning
            return String.format("%-3s  len=%-3d  %s", "", path.size(), expression);
        }
    }

    // ---------- Misc helpers ----------

    private static boolean inBounds(int n, int r, int c) {
        return 0 <= r && r < n && 0 <= c && c < n;
    }
    private static boolean isNumber(String s) {
        return s != null && !s.isEmpty() && Character.isDigit(s.trim().charAt(0));
    }
    private static boolean isOperator(String s) {
        if (s == null || s.isEmpty()) return false;
        char ch = s.trim().charAt(0);
        return ch == '+' || ch == '-' || ch == '*' || ch == '/';
    }
    private static Rational apply(Rational a, String op, Rational b) {
        switch (op) {
            case "+": return a.add(b);
            case "-": return a.sub(b);
            case "*": return a.mul(b);
            case "/": return a.div(b);
            default: throw new IllegalArgumentException("Invalid operator: " + op);
        }
    }
    private void setGrid(String[][] g) {
        int n = g.length;
        resizeTable(n);
        DefaultTableModel model = (DefaultTableModel) gridTable.getModel();
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                model.setValueAt(g[r][c], r, c);
    }
    private static String[][] getGridFromTable(JTable table) {
        int n = table.getRowCount();
        String[][] grid = new String[n][n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                Object val = table.getValueAt(r, c);
                if (val == null) return null;
                String s = val.toString().trim();
                if (s.isEmpty()) return null;
                grid[r][c] = s;
            }
        }
        return grid;
    }
    private String[][] getGridSafely() {
        if (gridTable.getRowCount() == 0) return null;
        return getGridFromTable(gridTable);
    }
    private static String buildExpression(String[][] grid, List<int[]> path) {
        StringBuilder sb = new StringBuilder();
        for (int[] p : path) sb.append(grid[p[0]][p[1]]);
        return sb.toString();
    }
    private static String formatSteps(String[][] grid, List<int[]> path) {
        StringBuilder sb = new StringBuilder();
        Rational cur = Rational.ofInt(Integer.parseInt(grid[path.get(0)[0]][path.get(0)[1]]));
        sb.append(cur);
        String op = null;
        for (int i = 1; i < path.size(); i++) {
            String cell = grid[path.get(i)[0]][path.get(i)[1]];
            if (isOperator(cell)) {
                op = cell;
            } else {
                Rational rhs = Rational.ofInt(Integer.parseInt(cell));
                Rational next = apply(cur, op, rhs);
                sb.append(op).append(rhs).append("=").append(next);
                if (i < path.size() - 1) sb.append(" -> ");
                cur = next;
                op = null;
            }
        }
        return sb.toString();
    }
    private void toast(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------- Exact Rational Type ----------
    static final class Rational {
        final BigInteger num; // reduced; sign on numerator
        final BigInteger den; // positive

        Rational(BigInteger num, BigInteger den) {
            if (den.signum() == 0) throw new ArithmeticException("Zero denominator");
            if (den.signum() < 0) { num = num.negate(); den = den.negate(); }
            BigInteger g = num.gcd(den);
            if (!g.equals(BigInteger.ONE)) {
                num = num.divide(g); den = den.divide(g);
            }
            this.num = num; this.den = den;
        }
        static Rational ofInt(int v) { return new Rational(BigInteger.valueOf(v), BigInteger.ONE); }
        static Rational parse(String s) {
            s = s.trim();
            if (s.contains(".")) {
                boolean neg = s.startsWith("-");
                if (neg) s = s.substring(1);
                String[] parts = s.split("\\.", -1);
                String whole = parts[0].isEmpty() ? "0" : parts[0];
                String frac  = parts.length > 1 ? parts[1] : "";
                String digits = whole + frac;
                BigInteger n = new BigInteger(digits.isEmpty() ? "0" : digits);
                BigInteger d = BigInteger.ONE;
                if (!frac.isEmpty()) d = BigInteger.TEN.pow(frac.length());
                if (neg) n = n.negate();
                return new Rational(n, d);
            } else {
                return new Rational(new BigInteger(s), BigInteger.ONE);
            }
        }
        Rational add(Rational o) { return new Rational(this.num.multiply(o.den).add(o.num.multiply(this.den)), this.den.multiply(o.den)); }
        Rational sub(Rational o) { return new Rational(this.num.multiply(o.den).subtract(o.num.multiply(this.den)), this.den.multiply(o.den)); }
        Rational mul(Rational o) { return new Rational(this.num.multiply(o.num), this.den.multiply(o.den)); }
        Rational div(Rational o) {
            if (o.num.signum() == 0) throw new ArithmeticException("Division by zero");
            return new Rational(this.num.multiply(o.den), this.den.multiply(o.num));
        }
        @Override public boolean equals(Object obj) {
            if (!(obj instanceof Rational)) return false;
            Rational o = (Rational) obj;
            return this.num.equals(o.num) && this.den.equals(o.den);
        }
        @Override public int hashCode() { return Objects.hash(num, den); }
        @Override public String toString() { return den.equals(BigInteger.ONE) ? num.toString() : (num + "/" + den); }
    }

    // ---------- JTable that auto-starts editing ----------
    static class EditableTable extends JTable {
        EditableTable(DefaultTableModel model) { super(model); }
        @Override
        public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
            super.changeSelection(rowIndex, columnIndex, toggle, extend);
            if (!isEditing() && editCellAt(rowIndex, columnIndex)) {
                Component editor = getEditorComponent();
                if (editor != null) editor.requestFocusInWindow();
            }
        }
    }

    /** Row header aligned with JTable rows that shows "Clear row i" buttons. */
    static class RowHeader extends JList<Integer> {
        private final JTable table;
        private final java.util.function.IntConsumer clearRowAction;

        RowHeader(JTable table, java.util.function.IntConsumer clearRowAction) {
            super(new AbstractListModel<Integer>() {
                @Override public int getSize() { return table.getRowCount(); }
                @Override public Integer getElementAt(int index) { return index; }
            });
            this.table = table;
            this.clearRowAction = clearRowAction;

            setFixedCellHeight(table.getRowHeight());
            setCellRenderer(new ButtonLikeRenderer(table));
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            // click to clear
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    int idx = locationToIndex(e.getPoint());
                    if (idx >= 0) clearRowAction.accept(idx);
                    repaint();
                }
            });

            // keep height synced when row height changes (e.g., LAF scaling)
            table.addPropertyChangeListener(evt -> {
                if ("rowHeight".equals(evt.getPropertyName())) {
                    setFixedCellHeight(table.getRowHeight());
                    revalidate(); repaint();
                }
            });
        }

        /** Call after the table model/size changes. */
        void syncToTable() {
            setFixedCellHeight(table.getRowHeight());
            setModel(new AbstractListModel<Integer>() {
                @Override public int getSize() { return table.getRowCount(); }
                @Override public Integer getElementAt(int index) { return index; }
            });
            revalidate(); repaint();
        }

        /** Renders each row header cell as a button-looking component. */
        static class ButtonLikeRenderer extends DefaultListCellRenderer {
            private final JTable table;
            ButtonLikeRenderer(JTable table) { this.table = table; }

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JButton btn = new JButton("Clear Row " + (index + 1));
                btn.setMargin(new Insets(2, 6, 2, 6));
                btn.setFocusPainted(false);

                btn.setBackground(new Color(112, 6, 29));      // AliceBlue
                btn.setForeground(new Color(222, 37, 66));
                btn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(222, 37, 66)),
                        BorderFactory.createEmptyBorder(2, 8, 2, 8)
                ));
                btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                // Match row height and a tidy width
                int h = table.getRowHeight();
                btn.setPreferredSize(new Dimension(110, h));
                btn.setMinimumSize(new Dimension(110, h));
                btn.setMaximumSize(new Dimension(110, h));
                return btn;
            }
        }
    }

    /** Generate a valid-looking MathMaze sample for any n×n:
     * numbers on (r+c)%2==0 and operators on (r+c)%2==1.
     * Top-left and bottom-right are numbers by construction.
     */
    private String[][] generateSample(int n) {
        String[][] g = new String[n][n];
        char[] ops = new char[]{'+','-','*','/'};
        Random rnd = new Random();

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if ( ((r + c) & 1) == 0 ) {
                    // number cell: allow 0..9
                    g[r][c] = Integer.toString(rnd.nextInt(10));
                } else {
                    // operator cell
                    g[r][c] = Character.toString(ops[rnd.nextInt(ops.length)]);
                }
            }
        }
        // ensure top-left and bottom-right numbers are not “0” to avoid trivial div-by-zero traps
        if ("0".equals(g[0][0])) g[0][0] = "1";
        if ("0".equals(g[n-1][n-1])) g[n-1][n-1] = "1";
        return g;
    }
}
