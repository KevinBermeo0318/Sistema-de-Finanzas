import sqlite3
from sqlite3 import Connection
from datetime import datetime
from typing import List, Dict, Any, Optional
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from fpdf import FPDF
from flask import Flask, request, jsonify, send_file
import io
import os

DB_FILE = os.path.join(os.path.dirname(__file__), 'data.db')


def get_conn() -> Connection:
    conn = sqlite3.connect(DB_FILE)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    conn = get_conn()
    cur = conn.cursor()
    cur.execute('''
    CREATE TABLE IF NOT EXISTS categories (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL UNIQUE
    )
    ''')
    cur.execute('''
    CREATE TABLE IF NOT EXISTS transactions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        amount REAL NOT NULL,
        type TEXT NOT NULL CHECK(type IN ('income','expense')),
        category_id INTEGER,
        description TEXT,
        date TEXT NOT NULL,
        FOREIGN KEY(category_id) REFERENCES categories(id)
    )
    ''')
    conn.commit()
    conn.close()


def add_category(name: str) -> int:
    conn = get_conn()
    cur = conn.cursor()
    try:
        cur.execute('INSERT INTO categories (name) VALUES (?)', (name,))
        conn.commit()
        return cur.lastrowid
    except sqlite3.IntegrityError:
        cur.execute('SELECT id FROM categories WHERE name=?', (name,))
        row = cur.fetchone()
        return row['id']
    finally:
        conn.close()


def list_categories() -> List[Dict[str, Any]]:
    conn = get_conn()
    cur = conn.cursor()
    cur.execute('SELECT id, name FROM categories ORDER BY name')
    rows = cur.fetchall()
    conn.close()
    return [dict(r) for r in rows]


def add_transaction(amount: float, ttype: str, category: Optional[str], description: str, date: str) -> int:
    conn = get_conn()
    cur = conn.cursor()
    cat_id = None
    if category:
        cat_id = add_category(category)
    cur.execute('INSERT INTO transactions (amount, type, category_id, description, date) VALUES (?,?,?,?,?)',
                (amount, ttype, cat_id, description, date))
    conn.commit()
    tid = cur.lastrowid
    conn.close()
    return tid


def list_transactions(start_date: Optional[str] = None, end_date: Optional[str] = None, category: Optional[str] = None) -> List[Dict[str, Any]]:
    conn = get_conn()
    cur = conn.cursor()
    q = '''SELECT t.id, t.amount, t.type, c.name as category, t.description, t.date
           FROM transactions t LEFT JOIN categories c ON t.category_id = c.id
        '''
    clauses = []
    params = []
    if start_date:
        clauses.append('date(t.date) >= date(?)')
        params.append(start_date)
    if end_date:
        clauses.append('date(t.date) <= date(?)')
        params.append(end_date)
    if category:
        clauses.append('c.name = ?')
        params.append(category)
    if clauses:
        q += ' WHERE ' + ' AND '.join(clauses)
    q += ' ORDER BY date(t.date) DESC'
    cur.execute(q, params)
    rows = cur.fetchall()
    conn.close()
    return [dict(r) for r in rows]


def monthly_report(year: int, month: int) -> Dict[str, Any]:
    start = f'{year:04d}-{month:02d}-01'
    dt = datetime(year, month, 28)
    # get last day safely by rolling forward
    import calendar
    last = calendar.monthrange(year, month)[1]
    end = f'{year:04d}-{month:02d}-{last:02d}'
    txs = list_transactions(start, end)
    df = pd.DataFrame(txs)
    if df.empty:
        return {"income": 0.0, "expense": 0.0, "by_category": {}, "transactions": []}
    income = df[df['type'] == 'income']['amount'].sum()
    expense = df[df['type'] == 'expense']['amount'].sum()
    by_cat = df.groupby('category')['amount'].sum().to_dict()
    return {"income": float(income), "expense": float(expense), "by_category": {k: float(v) for k, v in by_cat.items()}, "transactions": df.to_dict(orient='records')}


def plot_pie_by_category(report: Dict[str, Any]) -> bytes:
    by_cat = report.get('by_category', {})
    if not by_cat:
        fig, ax = plt.subplots()
        ax.text(0.5, 0.5, 'No data', ha='center', va='center')
    else:
        labels = list(by_cat.keys())
        sizes = list(by_cat.values())
        fig, ax = plt.subplots()
        ax.pie(sizes, labels=labels, autopct='%1.1f%%')
        ax.axis('equal')
    buf = io.BytesIO()
    fig.savefig(buf, format='png')
    plt.close(fig)
    buf.seek(0)
    return buf.read()


def export_excel(report: Dict[str, Any]) -> bytes:
    df = pd.DataFrame(report.get('transactions', []))
    buf = io.BytesIO()
    with pd.ExcelWriter(buf, engine='openpyxl') as writer:
        df.to_excel(writer, sheet_name='transactions', index=False)
        summary = pd.DataFrame([{'income': report.get('income', 0), 'expense': report.get('expense', 0)}])
        summary.to_excel(writer, sheet_name='summary', index=False)
    buf.seek(0)
    return buf.read()


def export_pdf(report: Dict[str, Any]) -> bytes:
    pdf = FPDF()
    pdf.add_page()
    pdf.set_font('Arial', 'B', 16)
    pdf.cell(0, 10, 'Monthly Report', ln=1)
    pdf.set_font('Arial', '', 12)
    pdf.cell(0, 8, f"Income: {report.get('income', 0):.2f}", ln=1)
    pdf.cell(0, 8, f"Expense: {report.get('expense', 0):.2f}", ln=1)
    pdf.ln(4)
    pdf.cell(0, 8, 'By Category:', ln=1)
    for k, v in report.get('by_category', {}).items():
        pdf.cell(0, 7, f" - {k}: {v:.2f}", ln=1)
    pdf.ln(4)
    pdf.cell(0, 8, 'Transactions:', ln=1)
    pdf.set_font('Arial', '', 10)
    for tx in report.get('transactions', []):
        pdf.multi_cell(0, 6, f"{tx.get('date')} | {tx.get('type')} | {tx.get('category')} | {tx.get('amount'):.2f} | {tx.get('description')}")
    return pdf.output(dest='S').encode('latin-1')


app = Flask(__name__)


@app.route('/init', methods=['POST'])
def init_route():
    init_db()
    return jsonify({'ok': True})


@app.route('/categories', methods=['GET', 'POST'])
def categories_route():
    if request.method == 'POST':
        data = request.json or {}
        name = data.get('name')
        if not name:
            return jsonify({'error': 'name required'}), 400
        cid = add_category(name)
        return jsonify({'id': cid, 'name': name})
    else:
        return jsonify(list_categories())


@app.route('/transactions', methods=['GET', 'POST'])
def transactions_route():
    if request.method == 'POST':
        data = request.json or {}
        try:
            amount = float(data.get('amount'))
            ttype = data.get('type')
            if ttype not in ('income', 'expense'):
                return jsonify({'error': 'type must be income or expense'}), 400
            category = data.get('category')
            description = data.get('description', '')
            date = data.get('date') or datetime.utcnow().strftime('%Y-%m-%d')
        except Exception:
            return jsonify({'error': 'invalid payload'}), 400
        tid = add_transaction(amount, ttype, category, description, date)
        return jsonify({'id': tid})
    else:
        start = request.args.get('start')
        end = request.args.get('end')
        category = request.args.get('category')
        txs = list_transactions(start, end, category)
        return jsonify(txs)


@app.route('/report/monthly', methods=['GET'])
def monthly_route():
    year = int(request.args.get('year', datetime.utcnow().year))
    month = int(request.args.get('month', datetime.utcnow().month))
    report = monthly_report(year, month)
    return jsonify(report)


@app.route('/report/monthly/plot', methods=['GET'])
def monthly_plot_route():
    year = int(request.args.get('year', datetime.utcnow().year))
    month = int(request.args.get('month', datetime.utcnow().month))
    report = monthly_report(year, month)
    img = plot_pie_by_category(report)
    return send_file(io.BytesIO(img), mimetype='image/png', download_name='plot.png')


@app.route('/report/monthly/export', methods=['GET'])
def monthly_export_route():
    year = int(request.args.get('year', datetime.utcnow().year))
    month = int(request.args.get('month', datetime.utcnow().month))
    fmt = request.args.get('format', 'excel')
    report = monthly_report(year, month)
    if fmt == 'excel':
        data = export_excel(report)
        return send_file(io.BytesIO(data), mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', download_name='report.xlsx')
    else:
        data = export_pdf(report)
        return send_file(io.BytesIO(data), mimetype='application/pdf', download_name='report.pdf')


if __name__ == '__main__':
    init_db()
    app.run(debug=True, port=5000)
